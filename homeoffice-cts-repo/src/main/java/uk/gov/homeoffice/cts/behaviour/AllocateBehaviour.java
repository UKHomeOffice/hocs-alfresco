package uk.gov.homeoffice.cts.behaviour;

import org.alfresco.model.ContentModel;
import org.alfresco.repo.security.authentication.AuthenticationUtil;
import org.alfresco.service.cmr.action.ActionService;
import org.alfresco.service.cmr.repository.NodeRef;
import org.alfresco.service.cmr.repository.NodeService;
import org.alfresco.service.cmr.security.*;
import org.alfresco.service.namespace.QName;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import uk.gov.homeoffice.cts.email.EmailService;
import uk.gov.homeoffice.cts.helpers.CtsFolderHelper;
import uk.gov.homeoffice.cts.model.CaseStatus;
import uk.gov.homeoffice.cts.model.CtsModel;
import uk.gov.homeoffice.cts.model.TaskStatus;
import uk.gov.homeoffice.cts.permissions.CtsPermissions;

import java.io.Serializable;
import java.util.*;

public class AllocateBehaviour implements PropertyUpdateBehaviour {

    private static final Logger LOGGER = LoggerFactory.getLogger(AllocateBehaviour.class);

    private NodeService nodeService;
    private PersonService personService;
    private PermissionService permissionService;
    private EmailService emailService;
    private ActionService actionService;
    private AuthorityService authorityService;
    private CtsFolderHelper ctsFolderHelper;
    private String workFlowEmailTemplateId;
    private String workFlowTeamEmailTemplateId;
    private String ctsUrl;
    private String dataServiceEndpoint;


    @Override
    public void onUpdateProperties(final NodeRef nodeRef, final Map<QName, Serializable> before, final Map<QName, Serializable> after) {
        checkEmail(nodeRef, before, after);

        //now set the permissions, need to do it as admin to ensure properties get set
        AuthenticationUtil.runAs(new AuthenticationUtil.RunAsWork<Map<String, Object>>() {
            @SuppressWarnings("synthetic-access")
            public Map<String, Object> doWork() throws Exception {
                updatePermissions(nodeRef, before, after);
                return null;
            }
        }, AuthenticationUtil.getAdminUserName());
    }

    /**
     * Method to check through whether we need to send out an email
     *
     * @param nodeRef
     * @param before
     * @param after
     */
    protected void checkEmail(NodeRef nodeRef, Map<QName, Serializable> before, Map<QName, Serializable> after) {
        String assignedUserBefore = (String) before.get(CtsModel.PROP_ASSIGNED_USER);
        String assignedUserAfter = (String) after.get(CtsModel.PROP_ASSIGNED_USER);

        String assignedTeamBefore = (String) before.get(CtsModel.PROP_ASSIGNED_TEAM);
        String assignedTeamAfter = (String) after.get(CtsModel.PROP_ASSIGNED_TEAM);

        String assignedUnitBefore = (String) before.get(CtsModel.PROP_ASSIGNED_UNIT);
        String assignedUnitAfter = (String) after.get(CtsModel.PROP_ASSIGNED_UNIT);

        if (assignedUserAfter != null && BehaviourHelper.hasChanged(assignedUserBefore, assignedUserAfter)) {
            LOGGER.debug(nodeRef + "Assigned user has changed so send email. Assigned User Before: " + assignedUserBefore + " Assigned User After: " + assignedUserAfter);
            //then see if that user wants the email or not
            sendEmailToUser(nodeRef, after);
        } else if (assignedTeamAfter != null && BehaviourHelper.hasChanged(assignedTeamBefore, assignedTeamAfter)) {
            LOGGER.debug(nodeRef + " Assigned Team Before: " + assignedTeamBefore + " Assigned Team After: " + assignedTeamAfter);
            //send to group
            if (assignedUserAfter == null) {
                sendEmailToGroup(assignedTeamAfter, nodeRef, after);
            }
        } else if (assignedUnitAfter != null && BehaviourHelper.hasChanged(assignedUnitBefore, assignedUnitAfter)) {
            LOGGER.debug(nodeRef + " Assigned Unit Before: " + assignedUnitBefore + " Assigned Unit After: " + assignedUnitAfter);
            //send to group
            if (assignedTeamAfter == null && assignedUserAfter == null) {
                sendEmailToGroup(assignedUnitAfter, nodeRef, after);
            }
        }
    }


    protected void sendEmailToUser(NodeRef nodeRef, Map<QName, Serializable> after) {
        String assignedUserAfter = (String) after.get(CtsModel.PROP_ASSIGNED_USER);
        String caseStatus = (String) after.get(CtsModel.PROP_CASE_STATUS);
        Serializable caseTask = after.get(CtsModel.PROP_CASE_TASK);
        Serializable creator = after.get(ContentModel.PROP_CREATOR);

        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("Should we send an email? assignedUserAfter:" + assignedUserAfter + " caseStatus:" + caseStatus + "  caseTask:" + caseTask + " creator:" + creator);
        }

        //when a case is created the creator is set as the assigned user, we don't want to send an email to them
        if ((caseStatus.equals(CaseStatus.NEW.getStatus()) && caseTask.equals(TaskStatus.CREATE_CASE.getStatus())) && (creator.equals(assignedUserAfter))) {
            LOGGER.debug("Case status is New and task is Create case and the user is the person who created it so no email");
            //the case gets assigned to the creator as part of the creation process so don't send them an email
            return;
        } else {
//            LOGGER.debug("building Notify properties");
//            LOGGER.debug("user = " + after.get(CtsModel.PROP_ASSIGNED_USER).toString());
//            LOGGER.debug("link = " + getCtsUrl() + "/cts/cases/view/" + nodeRef.getId());
            NodeRef personNodeRef;
            try {
                personNodeRef = getPersonService().getPerson(assignedUserAfter);
            } catch (NoSuchPersonException e) {
                return;
            }
            if (personNodeRef == null) {
                //don't go on
                return;
            }
            Map<QName, Serializable> personProps = getNodeService().getProperties(personNodeRef);
            String emailAddress = personProps.get(ContentModel.PROP_EMAIL).toString();
            HashMap<String, String> personalisation = new HashMap<>();
            personalisation.put("user", after.get(CtsModel.PROP_ASSIGNED_USER).toString());
            personalisation.put("link", getCtsUrl() + "/cts/cases/view/" + nodeRef.getId());
            LOGGER.debug("templateid = " + getWorkFlowEmailTemplateId() + " | emailAddress = " + emailAddress + " | Personalisation = " + personalisation);
            emailService.sendEmail(getWorkFlowEmailTemplateId(), emailAddress, personalisation);
            LOGGER.debug("After email send");
        }
    }


    protected void sendEmailToGroup(String assignedGroupAfter, NodeRef nodeRef, Map<QName, Serializable> after) {
        NodeRef groupNodeRef = getAuthorityService().getAuthorityNodeRef(assignedGroupAfter);
        if (groupNodeRef == null) {
            //don't go on
            return;
        }
        RestTemplate restTemplate = new RestTemplate();

        try {
            ResponseEntity<AllocateBehaviourTeamEmail> teamEmailResponseEntity =  restTemplate.getForEntity(getDataServiceEndpoint() + "/team/" + after.get(CtsModel.PROP_ASSIGNED_TEAM), AllocateBehaviourTeamEmail.class);

            HashMap<String, String> personalisation = new HashMap<>();
            personalisation.put("user", teamEmailResponseEntity.getBody().displayName);
            personalisation.put("link", getCtsUrl() + "/cts/cases/view/" + nodeRef.getId());

            LOGGER.debug("templateid = " + getWorkFlowTeamEmailTemplateId() + " | emailAddress = " + teamEmailResponseEntity.getBody().email + " | Personalisation = " + personalisation);
            emailService.sendEmail(getWorkFlowTeamEmailTemplateId(), teamEmailResponseEntity.getBody().email, personalisation);
        } catch (RestClientException e) {
            LOGGER.debug("Error when trying to get team email address from data service, email not sent to team");
//            e.printStackTrace();
        }

    }


    private void updatePermissions(NodeRef nodeRef, Map<QName, Serializable> before, Map<QName, Serializable> after) {
        String assignedUserBefore = (String) before.get(CtsModel.PROP_ASSIGNED_USER);
        String assignedUserAfter = (String) after.get(CtsModel.PROP_ASSIGNED_USER);

        String assignedTeamBefore = (String) before.get(CtsModel.PROP_ASSIGNED_TEAM);
        String assignedTeamAfter = (String) after.get(CtsModel.PROP_ASSIGNED_TEAM);

        String assignedUnitBefore = (String) before.get(CtsModel.PROP_ASSIGNED_UNIT);
        String assignedUnitAfter = (String) after.get(CtsModel.PROP_ASSIGNED_UNIT);

        boolean allocationChanged = false;
        //deal with changes in the assigned user
        if (BehaviourHelper.hasChanged(assignedUserBefore, assignedUserAfter)) {
            LOGGER.debug(nodeRef + " Assigned User Before: " + assignedUserBefore + " Assigned User After: " + assignedUserAfter);

            allocationChanged = true;
            //The assigned user has changed so need to give them the permissions set above the case in the file plan
            if (assignedUserBefore != null && assignedUserAfter == null) {
                //then the old user should have their permissions removed and no users will have permissions
                removeUserPermissions(nodeRef, assignedUserBefore);
            } else {
                if (assignedUserBefore != null) {
                    //remove the old users permissions
                    removeUserPermissions(nodeRef, assignedUserBefore);
                }
                //check the authority is an authority
                if (assignedUserAfter != null && getAuthorityService().authorityExists(assignedUserAfter)) {
                    LOGGER.debug("Assigned user has changed so change permissions");
                    //now add them for the new authority
                    addPermissionsForAllocatedUser(nodeRef, assignedUserAfter);
                }
            }
        }
        //now deal with any changes in the assigned unit or team
        if (BehaviourHelper.hasChanged(assignedTeamBefore, assignedTeamAfter)) {

            LOGGER.debug(nodeRef + " Assigned Team Before: " + assignedTeamBefore + " Assigned Team After: " + assignedTeamAfter);

            allocationChanged = true;
            if (assignedTeamBefore != null) {
                removeGroupPermissions(nodeRef, assignedTeamBefore);
            }
            if (assignedTeamAfter != null) {
                getPermissionService().setPermission(nodeRef, assignedTeamAfter, CtsPermissions.ALLOCATE, true);
            }
        }

        if (BehaviourHelper.hasChanged(assignedUnitBefore, assignedUnitAfter)) {
            LOGGER.debug(nodeRef + " Assigned Unit Before: " + assignedUnitBefore + " Assigned Unit After: " + assignedUnitAfter);

            allocationChanged = true;
            if (assignedUnitBefore != null) {
                removeGroupPermissions(nodeRef, assignedUnitBefore);
            }
            if (assignedUnitAfter != null) {
                getPermissionService().setPermission(nodeRef, assignedUnitAfter, CtsPermissions.ALLOCATE, true);
            }
        }
        if (allocationChanged) {
            updateOwnerUpdatedDatetime(nodeRef);
        }
    }

    private void updateOwnerUpdatedDatetime(NodeRef nodeRef) {
        nodeService.setProperty(nodeRef, CtsModel.PROP_OWNER_UPDATED_DATETIME, new Date());
    }

    /**
     * When a user is allocated a case they need to be given appropriate permissions.
     * These permissions should match their role on folders higher up as the case does
     * not inherit permissions. So Whatever role the user has on the parent node they should
     * get at this point.
     *
     * @param nodeRef
     * @param assignedUserAfter
     */
    private void addPermissionsForAllocatedUser(NodeRef nodeRef, String assignedUserAfter) {
        LOGGER.debug(nodeRef + " Adding permissions for user: " + assignedUserAfter);
        NodeRef parentNodeRef = getNodeService().getPrimaryParent(nodeRef).getParentRef();
        Set<String> allowedPermissions = getUsersPermissions(parentNodeRef, assignedUserAfter);

        for (String permission : allowedPermissions) {
            if (permission.equals(CtsPermissions.CASE_VIEWER)) {
                LOGGER.debug(nodeRef + " Add permission " + CtsPermissions.ACTUAL_CASE_VIEWER + " for user: " + assignedUserAfter);
                //then add this permission which allows the user to add documents and minutes but not edit the case
                getPermissionService().setPermission(nodeRef, assignedUserAfter, CtsPermissions.ACTUAL_CASE_VIEWER, true);
            } else {
                LOGGER.debug(nodeRef + " Add permission " + permission + " for user: " + assignedUserAfter);
                getPermissionService().setPermission(nodeRef, assignedUserAfter, permission, true);
            }
        }
    }

    private Set<String> getUsersPermissions(NodeRef parentNodeRef, String assignedUserAfter) {
        Set<AccessPermission> permissions = getPermissionService().getAllSetPermissions(parentNodeRef);
        Set<String> usersPermissions = new HashSet<>();

        for (AccessPermission permission : permissions) {
            String authority = permission.getAuthority();
            if (permission.getAuthorityType() == AuthorityType.GROUP) {
                //check if user is in group
                Set<String> groups = getAuthorityService().getAuthoritiesForUser(assignedUserAfter);
                if (groups.contains(permission.getAuthority()) && permission.getAccessStatus() == AccessStatus.ALLOWED) {
                    usersPermissions.add(permission.getPermission());
                }
            } else if (permission.getAuthorityType() == AuthorityType.USER) {
                if (authority.equals(assignedUserAfter) && permission.getAccessStatus() == AccessStatus.ALLOWED) {
                    usersPermissions.add(permission.getPermission());
                }
            } else {
                LOGGER.debug("Unknown authority type " + permission.getAuthorityType());
            }
        }
        return usersPermissions;
    }

    /**
     * Method that will remove all permissions for a given user
     *
     * @param nodeRef
     * @param oldUser
     */
    private void removeUserPermissions(NodeRef nodeRef, String oldUser) {
        LOGGER.debug(nodeRef + " Removing permissions for user: " + oldUser);
        removeAuthorityPermissions(nodeRef, oldUser);
    }

    /**
     * Method that will remove all permissions for a given group
     *
     * @param nodeRef
     * @param oldGroup
     */
    private void removeGroupPermissions(NodeRef nodeRef, String oldGroup) {
        LOGGER.debug(nodeRef + " Removing permissions for group: " + oldGroup);
        removeAuthorityPermissions(nodeRef, oldGroup);
    }

    /**
     * Method that will remove all permissions for a given authority
     *
     * @param nodeRef
     * @param oldAuthority
     */
    private void removeAuthorityPermissions(NodeRef nodeRef, String oldAuthority) {
        Set<AccessPermission> perms = getPermissionService().getAllSetPermissions(nodeRef);
        for (AccessPermission perm : perms) {
            if (perm.getAuthority().equals(oldAuthority)) {
                getPermissionService().deletePermission(nodeRef, perm.getAuthority(), perm.getPermission());
            }
        }
    }

    private PersonService getPersonService() {
        return personService;
    }

    public void setPersonService(PersonService personService) {
        this.personService = personService;
    }

    private NodeService getNodeService() {
        return nodeService;
    }

    public void setNodeService(NodeService nodeService) {
        this.nodeService = nodeService;
    }

    private AuthorityService getAuthorityService() {
        return authorityService;
    }

    public void setAuthorityService(AuthorityService authorityService) {
        this.authorityService = authorityService;
    }

    private ActionService getActionService() {
        return actionService;
    }

    public void setActionService(ActionService actionService) {
        this.actionService = actionService;
    }

    private String getCtsUrl() {
        return ctsUrl;
    }

    public void setCtsUrl(String ctsUrl) {
        this.ctsUrl = ctsUrl;
    }

    private CtsFolderHelper getCtsFolderHelper() {
        return ctsFolderHelper;
    }

    public void setCtsFolderHelper(CtsFolderHelper ctsFolderHelper) {
        this.ctsFolderHelper = ctsFolderHelper;
    }

    private PermissionService getPermissionService() {
        return permissionService;
    }

    public void setPermissionService(PermissionService permissionService) {
        this.permissionService = permissionService;
    }

    public String getDataServiceEndpoint() {
        return dataServiceEndpoint;
    }

    public void setDataServiceEndpoint(String dataServiceEndpoint) {
        this.dataServiceEndpoint = dataServiceEndpoint;
    }

    public EmailService getEmailService() {
        return emailService;
    }

    public void setEmailService(EmailService emailService) {
        this.emailService = emailService;
    }

    public String getWorkFlowEmailTemplateId() {
        return workFlowEmailTemplateId;
    }

    public void setWorkFlowEmailTemplateId(String workFlowEmailTemplateId) {
        this.workFlowEmailTemplateId = workFlowEmailTemplateId;
    }

    public String getWorkFlowTeamEmailTemplateId() {
        return workFlowTeamEmailTemplateId;
    }

    public void setWorkFlowTeamEmailTemplateId(String workFlowTeamEmailTemplateId) {
        this.workFlowTeamEmailTemplateId = workFlowTeamEmailTemplateId;
    }
}


