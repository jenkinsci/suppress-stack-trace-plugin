package org.jenkinsci.plugins.suppress_stack_trace;

import com.gargoylesoftware.htmlunit.html.HtmlPage;
import hudson.security.AccessDeniedException2;
import hudson.security.GlobalMatrixAuthorizationStrategy;
import hudson.security.HudsonPrivateSecurityRealm;
import hudson.util.PluginServletFilter;
import jenkins.model.Jenkins;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.HudsonTestCase;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.JenkinsRule.WebClient;
import org.kohsuke.stapler.HttpResponse;
import org.kohsuke.stapler.HttpResponses;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * @author Kohsuke Kawaguchi
 */
public class SuppressionFilterTest {

    private HudsonPrivateSecurityRealm realm;
    private GlobalMatrixAuthorizationStrategy strategy;
    private SuppressionFilter filter;
    private PluginImpl plugin;
    
    @Rule
    public JenkinsRule jr = new CustomRule();
    private Jenkins jenkins;



    /**
     * This should trigger a redirect
     */
    public HttpResponse doAuthenticationRequired() throws Exception {
        jenkins.checkPermission(Jenkins.ADMINISTER);
        return HttpResponses.ok();
    }

    @Before
    public void setUp() throws Exception {
        jenkins = jr.getInstance();
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

    @After
    public void tearDown() throws Exception {
        if (plugin!=null)
            plugin.stop();
    }

    /**
     * We should cover all kinds of exceptions
     */
    @Test
    public void test1() throws Exception {
        WebClient wc = jr.createWebClient();
        wc.getOptions().setThrowExceptionOnFailingStatusCode(false);

        verifyErrorPage(wc.goTo("self/test1"));
        verifyErrorPage(wc.goTo("self/test2"));
        verifyErrorPage(wc.goTo("self/test3"));
    }

    /**
     * Jenkins internally uses an exception to trigger a sequence for an authentication.
     * We shouldn't interfere with that
     */
    @Test
    public void testAuthenticationTrigger() throws Exception {
        WebClient wc = jr.createWebClient();
        wc.getOptions().setThrowExceptionOnFailingStatusCode(false);

        // this should send us to the login page, not to the error page
        verifyLoginPage(wc.goTo("/self/authenticationRequired"));
        verifyLoginPage(wc.goTo("/self/authenticationRequiredInJelly"));
    }

    private void verifyLoginPage(HtmlPage r) {
        String payload = r.getWebResponse().getContentAsString();

        assertEquals(payload, 200, r.getWebResponse().getStatusCode());
        assertEquals(payload, "/login", r.getUrl().getPath());
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
    
    static class CustomRule extends JenkinsRule {
        public HttpResponse doTest1() throws Exception {
            throw new Exception();
        }

        public HttpResponse doTest2() throws Exception {
            throw new RuntimeException();
        }

        public HttpResponse doTest3() throws Exception {
            throw new LinkageError();
        }
    }
}
