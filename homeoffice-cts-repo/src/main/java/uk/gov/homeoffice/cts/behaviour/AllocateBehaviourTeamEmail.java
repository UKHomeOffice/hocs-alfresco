package uk.gov.homeoffice.cts.behaviour;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class AllocateBehaviourTeamEmail {

    public String displayName;

    public String email;
}
