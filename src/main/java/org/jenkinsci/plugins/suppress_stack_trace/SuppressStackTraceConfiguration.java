package org.jenkinsci.plugins.suppress_stack_trace;

import org.kohsuke.stapler.StaplerRequest;

import hudson.Extension;
import jenkins.model.GlobalConfiguration;
import jenkins.model.Jenkins;
import net.sf.json.JSONObject;

@Extension
public final class SuppressStackTraceConfiguration extends GlobalConfiguration {
    public static final String NOBODY = "nobody";
    public static final String USERS = "users";
    public static final String EVERYBODY = "everybody";

    private String suppressStactraceTo;

    public SuppressStackTraceConfiguration() {
        load();
    }

    @Override
    public boolean configure(StaplerRequest req, JSONObject json) throws FormException {
        req.bindJSON(this, json);
        return true;
    }

    public String getDisplayName() {
        return "Suppress Stack Trace";
    }

    public String getSuppressStactraceTo() {
        return suppressStactraceTo;
    }

    public void setSuppressStactraceTo(String suppressStactraceTo) {
        this.suppressStactraceTo = suppressStactraceTo;
        save();
    }

    public static SuppressStackTraceConfiguration get() {
        return Jenkins.getInstance().getInjector().getInstance(SuppressStackTraceConfiguration.class);
    }

}
