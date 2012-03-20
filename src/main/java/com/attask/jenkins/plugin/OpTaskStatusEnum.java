package com.attask.jenkins.plugin;

/**
 * Created by IntelliJ IDEA.
 * User: nicholasstewart
 * Date: 2/8/12
 * Time: 3:18 PM
 * To change this template use File | Settings | File Templates.
 */
public enum OpTaskStatusEnum {

    
    /**
     * New. A newly entered Operational Task. This is the default status. Value is "NEW".
     **/
    NEW("NEW", false, "NEW"),

    /**
     * In Progress. A request is currently assigned and being worked on. Value is "INP".
     **/
    IN_PROGRESS("INP", false, "INP"),

    /**
     * Resolved. Request has been resolved and is awaiting verification for completion. Value is "RLV".
     **/
    RESOLVED("RLV", true, "CLS");

    OpTaskStatusEnum(String val, boolean isStatusComplete,  String equatesWith) {
        _value = val;
        _isStatusComplete = isStatusComplete;
        _equatesWith = equatesWith;
    }

    public String getValue() {
        return _value;
    }

    public void setValue(String _value) {
        this._value = _value;
    }

    public boolean isStatusComplete() {
        return _isStatusComplete;
    }

    public void setIsStatusComplete(boolean _isStatusComplete) {
        this._isStatusComplete = _isStatusComplete;
    }

    public String getEquatesWith() {
        return _equatesWith;
    }

    public void setEquatesWith(String _equatesWith) {
        this._equatesWith = _equatesWith;
    }

    protected String _value;
    protected boolean _isStatusComplete;
    protected String _equatesWith;

}