package com.devicehive.shim.api;

import java.util.Objects;

public abstract class RequestBody {

    protected String action;

    protected RequestBody(String action) {
        this.action = action;
    }

    public String getAction() {
        return action;
    }

    public void setAction(String action) {
        this.action = action;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof RequestBody)) return false;
        RequestBody body = (RequestBody) o;
        return Objects.equals(action, body.action);
    }

    @Override
    public int hashCode() {
        return Objects.hash(action);
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("Body{");
        sb.append("action='").append(action).append('\'');
        sb.append('}');
        return sb.toString();
    }

}
