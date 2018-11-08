package uk.gov.homeoffice.cts.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.alfresco.service.namespace.QName;

import java.io.Serializable;
import java.io.UnsupportedEncodingException;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class AuditMessage {

    @JsonProperty(value="uuid")
    private final UUID uuid;

    @JsonProperty(value="timestamp")
    @JsonFormat(pattern = "YYYY-MM-dd'T'HH:mm:ss")
    private final Date timestamp;

    @JsonProperty(value="caseReference")
    private final String caseReference;

    @JsonProperty(value="data")
    private final Map<String, String> afterMap;

    public AuditMessage(Map<QName, Serializable> after) throws UnsupportedEncodingException {
        uuid = UUID.randomUUID();
        timestamp = new Date();
        afterMap = transformMap(after);
        caseReference = afterMap.get("urnSuffix");
    }

    private static Map<String, String> transformMap(Map<QName, Serializable> map) throws UnsupportedEncodingException {
        Map<String, String> retMap = new HashMap<>();

        for(Map.Entry<QName, Serializable> entry : map.entrySet()) {
            //System.out.print(entry);
            String value = "null";
            if(entry.getValue() != null) {
                value = entry.getValue().toString();
            }

            retMap.put(entry.getKey().getLocalName(), value );
        }

        return retMap;
    }
}
