package org.jenkinsci.plugins.suppress_stack_trace;

import com.gargoylesoftware.htmlunit.html.HtmlPage;
import hudson.security.AccessDeniedException2;
import hudson.security.GlobalMatrixAuthorizationStrategy;
import hudson.security.HudsonPrivateSecurityRealm;
import hudson.util.PluginServletFilter;
import jenkins.model.Jenkins;

import static org.jenkinsci.plugins.suppress_stack_trace.SuppressStackTraceConfiguration.EVERYBODY;
import static org.jenkinsci.plugins.suppress_stack_trace.SuppressStackTraceConfiguration.NOBODY;
import static org.jenkinsci.plugins.suppress_stack_trace.SuppressStackTraceConfiguration.USERS;

import org.jvnet.hudson.test.HudsonTestCase;
import org.kohsuke.stapler.HttpResponse;
import org.kohsuke.stapler.HttpResponses;

/**
 * @author Kohsuke Kawaguchi
 */
public class SuppressionFilterTest extends HudsonTestCase {

    private HudsonPrivateSecurityRealm realm;
    private GlobalMatrixAuthorizationStrategy strategy;
    private SuppressionFilter filter;
    private PluginImpl plugin;

    public HttpResponse doTest1() throws Exception {
        throw new Exception();
    }

    public HttpResponse doTest2() throws Exception {
        throw new RuntimeException();
    }

    public HttpResponse doTest3() throws Exception {
        throw new LinkageError();
    }

    /**
     * This should trigger a redirect
     */
    public HttpResponse doAuthenticationRequired() throws Exception {
        Jenkins.getInstance().checkPermission(Jenkins.ADMINISTER);
        return HttpResponses.ok();
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        realm = new HudsonPrivateSecurityRealm(true, false, null);
        jenkins.setSecurityRealm(realm);
        realm.createAccount("alice", "alice");
        realm.createAccount("bob","bob");
        strategy = new GlobalMatrixAuthorizationStrategy();
        jenkins.setAuthorizationStrategy(strategy);
        strategy.add(Jenkins.ADMINISTER, "alice");
        strategy.add(Jenkins.READ,"anonymous");

        plugin = new PluginImpl();
        plugin.start();
    }

    @Override
    protected void tearDown() throws Exception {
        if (plugin!=null)
            plugin.stop();
        super.tearDown();
    }

    /**
     * We should cover all kinds of exceptions
     */
    public void test1() throws Exception {
        WebClient wc = createWebClient();
        wc.setThrowExceptionOnFailingStatusCode(false);

        verifyErrorPage(wc.goTo("/self/test1"));
        verifyErrorPage(wc.goTo("/self/test2"));
        verifyErrorPage(wc.goTo("/self/test3"));
    }

    /**
     * Test with Suppers stack trace to nobody configured
     */
    public void test2() throws Exception {
        SuppressStackTraceConfiguration config = SuppressStackTraceConfiguration.get();
        config.setSuppressStactraceTo(NOBODY);
        WebClient wc = createWebClient();
        wc.setThrowExceptionOnFailingStatusCode(false);

        verifyStackTrace(wc.goTo("/self/test1"));
        verifyStackTrace(wc.goTo("/self/test2"));
        verifyStackTrace(wc.goTo("/self/test3"));
    }


    /**
     * Test with Suppers stack trace to users configured
     */
    public void test3() throws Exception {
        SuppressStackTraceConfiguration config = SuppressStackTraceConfiguration.get();
        config.setSuppressStactraceTo(USERS);
        test1();
    }


    /**
     * Test with Suppers stack trace to everybody configured
     */
    public void test4() throws Exception {
        SuppressStackTraceConfiguration config = SuppressStackTraceConfiguration.get();
        config.setSuppressStactraceTo(EVERYBODY);
        test1();
    }

    /**
     * Jenkins internally uses an exception to trigger a sequence for an authentication.
     * We shouldn't interfere with that
     */
    public void testAuthenticationTrigger() throws Exception {
        WebClient wc = createWebClient();
        wc.setThrowExceptionOnFailingStatusCode(false);

        // this should send us to the login page, not to the error page
        verifyLoginPage(wc.goTo("/self/authenticationRequired"));
        verifyLoginPage(wc.goTo("/self/authenticationRequiredInJelly"));
    }

    private void verifyLoginPage(HtmlPage r) {
        String payload = r.getWebResponse().getContentAsString();

        assertEquals(payload, 200, r.getWebResponse().getStatusCode());
        assertEquals(payload, "/login", r.getWebResponse().getUrl().getPath());
    }

    private void verifyErrorPage(HtmlPage r) {
        assertEquals(500, r.getWebResponse().getStatusCode());
        assertEquals("text/html", r.getWebResponse().getContentType());
        String payload = r.getWebResponse().getContentAsString();

        // shouldn't contain anything that looks like an exception
        assertTrue(payload, !payload.contains("org.jenkinsci."));
        assertTrue(payload, !payload.contains("Exception"));
        assertTrue(payload, payload.contains("https://wiki.jenkins-ci.org/display/JENKINS/Suppress+Stack+Trace+Plugin"));
    }

    private void verifyStackTrace(HtmlPage r) {
        assertEquals(true, r.getWebResponse().getStatusCode()==500);
        assertEquals(true, "text/html".equals(r.getWebResponse().getContentType()));
        String payload = r.getWebResponse().getContentAsString();

        assertTrue(payload, payload.contains("Stack trace"));
        assertTrue(payload, payload.contains("Exception"));
    }
}
