package org.jenkinsci.plugins.suppress_stack_trace;

import hudson.Plugin;
import hudson.security.UnwrapSecurityExceptionFilter;
import hudson.util.PluginServletFilter;

/**
 * @author Kohsuke Kawaguchi
 */
public class PluginImpl extends Plugin {
    private SuppressionFilter filter1 = new SuppressionFilter();
    private UnwrapSecurityExceptionFilter filter2 = new UnwrapSecurityExceptionFilter();

    @Override
    public void start() throws Exception {
        PluginServletFilter.addFilter(filter1);
        PluginServletFilter.addFilter(filter2); // we need the jelly tag unwrapping to happen before the exception gets to SuppressionFilter
    }

    @Override
    public void stop() throws Exception {
        PluginServletFilter.removeFilter(filter1);
        PluginServletFilter.removeFilter(filter2);
    }
}
