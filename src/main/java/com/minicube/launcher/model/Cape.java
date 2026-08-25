package com.minicube.launcher.model;

/** Cape possedee par un compte Microsoft, telle que renvoyee par l'API Minecraft Services. */
public class Cape {

    private String id;
    private String alias;
    private String url;
    /** ACTIVE ou INACTIVE. */
    private String state;

    public Cape() {
    }

    public Cape(String id, String alias, String url, String state) {
        this.id = id;
        this.alias = alias;
        this.url = url;
        this.state = state;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getAlias() {
        return alias == null || alias.isBlank() ? "Cape" : alias;
    }

    public void setAlias(String alias) {
        this.alias = alias;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getState() {
        return state;
    }

    public void setState(String state) {
        this.state = state;
    }

    public boolean isActive() {
        return "ACTIVE".equalsIgnoreCase(state);
    }

    @Override
    public String toString() {
        return getAlias();
    }
}
