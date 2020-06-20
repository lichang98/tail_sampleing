package me.lc.bean;

import java.io.Serializable;
import java.util.List;
import java.util.Map;

/**
 * This class defines the content of middle process service response
 */
public class MiddleProcessorResponseBean implements Serializable {

    private Map<String, List<String>> badTraces;
    private String clientId;
    private String isLast;

    public MiddleProcessorResponseBean() {
    }

    public MiddleProcessorResponseBean(Map<String, List<String>> badTraces, String clientId, String isLast) {
        this.badTraces = badTraces;
        this.clientId = clientId;
        this.isLast = isLast;
    }

    public Map<String, List<String>> getBadTraces() {
        return badTraces;
    }

    public void setBadTraces(Map<String, List<String>> badTraces) {
        this.badTraces = badTraces;
    }

    public String getClientId() {
        return clientId;
    }

    public void setClientId(String clientId) {
        this.clientId = clientId;
    }

    public String getIsLast() {
        return isLast;
    }

    public void setIsLast(String isLast) {
        this.isLast = isLast;
    }

    @Override
    public String toString() {
        return "MiddleProcessorResponseBean{" +
                "badTraces=" + badTraces +
                ", clientId='" + clientId + '\'' +
                ", isLast='" + isLast + '\'' +
                '}';
    }
}
