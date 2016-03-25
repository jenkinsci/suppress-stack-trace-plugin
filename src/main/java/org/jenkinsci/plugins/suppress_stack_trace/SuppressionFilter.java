package org.jenkinsci.plugins.suppress_stack_trace;

import jenkins.model.Jenkins;
import org.acegisecurity.AccessDeniedException;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

import static javax.servlet.http.HttpServletResponse.SC_INTERNAL_SERVER_ERROR;
import static org.jenkinsci.plugins.suppress_stack_trace.SuppressStackTraceConfiguration.EVERYBODY;
import static org.jenkinsci.plugins.suppress_stack_trace.SuppressStackTraceConfiguration.NOBODY;
import static org.jenkinsci.plugins.suppress_stack_trace.SuppressStackTraceConfiguration.USERS;

import org.kohsuke.stapler.Stapler;

/**
 * @author Kohsuke Kawaguchi
 */
public class SuppressionFilter implements Filter {
    public void init(FilterConfig filterConfig) throws ServletException {
        // no-op
    }

    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
        try {
            chain.doFilter(request,response);
        } catch (Throwable e) {
            if (e instanceof AccessDeniedException) {
                throw (AccessDeniedException) e;
            }
            if (e instanceof ServletException) {
                if (((ServletException)e).getRootCause() instanceof AccessDeniedException)
                    throw (ServletException)e; // this exception needs to be pass through since Jenkins has a filter that reacts to this
            }
            if (Stapler.isSocketException(e)) {
                return;
            }

            if (showStackTrace(e)) {
                // thing we can throw without wrapping
                if (e instanceof IOException)
                    throw (IOException)e;
                if (e instanceof ServletException)
                    throw (ServletException)e;
                if (e instanceof RuntimeException)
                    throw (RuntimeException)e;
                if (e instanceof Error)
                    throw (Error)e;

                // ServletException chaining is messed up. We go extra mile here to really
                // chain the cause.
                ServletException x = new ServletException("Request processing failed", e);
                try {
                    if (x.getCause() == null)   x.initCause(e);
                } catch (IllegalStateException _) {
                    // just in case.
                }

                throw x;
            } else if (request instanceof HttpServletRequest && response instanceof HttpServletResponse) { 
                // We cannot decorate the stuff for non-Http responses
                String errorId = reportError((HttpServletRequest) request,e);

                try {
                    HttpServletResponse rsp = (HttpServletResponse)response;
                    rsp.setStatus(SC_INTERNAL_SERVER_ERROR);
                    rsp.setContentType("text/html;charset=UTF-8");
                    rsp.setHeader("Cache-Control","no-cache,must-revalidate");
                    PrintWriter w = null;
                    try {
                        w = rsp.getWriter();
                    } catch (IllegalStateException x) {
                        // stream mode?
                        w = new PrintWriter(new OutputStreamWriter(rsp.getOutputStream(),"UTF-8"));
                    }
                    w.println("<html><head><title>"+Messages.SuppressionFilter_Title()+"</title><body>");
                    w.println("<p>"+Messages.SuppressionFilter_ContactAdmin(errorId)+"</p>");
                    w.println(Messages.SuppressionFilter_Footer());
                    w.println("</body></html>");
                    w.close();
                } catch (Error error) {
                    throw error; // We propagate errors upstairs
                } catch (Throwable x) {
                    // if we fail to report this error, bail out
                    throw new ServletException(Messages.SuppressionFilter_ContactAdmin(errorId)); // not chaining x since it might contain something
                }
            }
        }
    }

    /**
     * Report an error and then generate a unique ID for that error.
     */
    protected String reportError(HttpServletRequest req, Throwable e) {
        String id = UUID.randomUUID().toString();
        LOGGER.log(Level.WARNING, "Request processing failed. URI=" + req.getRequestURI() + " clientIP=" + req.getRemoteAddr() + " ErrorID=" + id, e);
        return id;
    }

    /**
     * Should we show this stack trace to the requesting user?
     */
    protected boolean showStackTrace(Throwable t) {
        SuppressStackTraceConfiguration config = SuppressStackTraceConfiguration.get();
        String value = config.getSuppressStactraceTo();
        boolean ret = false;
        if(EVERYBODY.equals(value)){
            ret = false;
        } else if(NOBODY.equals(value)){
            ret = true;
        } else {
            ret = Jenkins.getInstance().hasPermission(Jenkins.ADMINISTER);
        }
        return ret;
    }

    public void destroy() {
        // no-op
    }

    private static final Logger LOGGER = Logger.getLogger(SuppressionFilter.class.getName());
}
