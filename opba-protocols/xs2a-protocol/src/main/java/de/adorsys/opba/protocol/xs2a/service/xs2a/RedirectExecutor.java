package de.adorsys.opba.protocol.xs2a.service.xs2a;

import de.adorsys.opba.protocol.xs2a.config.protocol.ProtocolConfiguration;
import de.adorsys.opba.protocol.xs2a.domain.dto.messages.Redirect;
import de.adorsys.opba.protocol.xs2a.service.ContextUtil;
import de.adorsys.opba.protocol.xs2a.service.xs2a.context.BaseContext;
import de.adorsys.opba.protocol.xs2a.service.xs2a.context.LastRedirectionTarget;
import de.adorsys.opba.protocol.xs2a.service.xs2a.context.Xs2aContext;
import lombok.RequiredArgsConstructor;
import org.flowable.engine.delegate.DelegateExecution;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.util.function.Function;

/**
 * Service that publishes {@link Redirect} result to internal event bus, meaning the user should be redirected
 * to the URL generated by this service.
 */
@Service
@RequiredArgsConstructor
public class RedirectExecutor {

    private final ProtocolConfiguration configuration;
    private final ApplicationEventPublisher applicationEventPublisher;

    /**
     * Redirects PSU to some page (or emits FinTech redirection required) by performing interpolation of the
     * string returned by {@code redirectSelector}
     * @param execution Execution context of the current process
     * @param context Current XS2A context
     * @param redirectSelector Redirection URL configurer - selects which URL template to use
     */
    public void redirect(
            DelegateExecution execution,
            Xs2aContext context,
            Function<ProtocolConfiguration.Redirect, String> redirectSelector) {
        String uiScreenUri = redirectSelector.apply(configuration.getRedirect());
        redirect(execution, context, uiScreenUri, null);
    }

    /**
     * Redirects PSU to some page (or emits FinTech redirection required) by performing interpolation of the
     * string returned by {@code uiScreenUriSpel}
     * @param execution Execution context of the current process
     * @param context Current XS2A context
     * @param uiScreenUriSpel UI screen SpEL expression to interpolate
     * @param destinationUri URL where UI screen should redirect user to if he clicks OK (i.e. to ASPSP redirection
     *                       where user must click OK button in order to be redirected to ASPSP)
     */
    public void redirect(
        DelegateExecution execution,
        Xs2aContext context,
        String uiScreenUriSpel,
        String destinationUri
    ) {
        redirect(execution, context, uiScreenUriSpel, destinationUri, Redirect.RedirectBuilder::build);
    }

    /**
     * Redirects PSU to some page (or emits FinTech redirection required) by performing interpolation of the
     * string returned by {@code uiScreenUriSpel}
     * @param execution Execution context of the current process
     * @param context Current XS2A context
     * @param uiScreenUriSpel UI screen SpEL expression to interpolate
     * @param destinationUri URL where UI screen should redirect user to if he clicks OK (i.e. to ASPSP redirection
     *                       where user must click OK button in order to be redirected to ASPSP)
     * @param eventFactory Allows to construct custom event with redirection parameters.
     */
    public void redirect(
        DelegateExecution execution,
        Xs2aContext context,
        String uiScreenUriSpel,
        String destinationUri,
        Function<Redirect.RedirectBuilder, ? extends Redirect> eventFactory
    ) {
        setDestinationUriInContext(execution, destinationUri);

        URI screenUri = URI.create(
            ContextUtil.evaluateSpelForCtx(
                uiScreenUriSpel,
                execution,
                context
            )
        );
        Redirect.RedirectBuilder redirect = Redirect.builder();
        redirect.processId(execution.getRootProcessInstanceId());
        redirect.executionId(execution.getId());
        redirect.redirectUri(screenUri);

        setUiUriInContext(execution, screenUri);

        applicationEventPublisher.publishEvent(eventFactory.apply(redirect));
    }

    private void setUiUriInContext(DelegateExecution execution, URI screenUri) {
        ContextUtil.getAndUpdateContext(
            execution,
            (BaseContext ctx) -> {
                LastRedirectionTarget target = getOrCreateLastRedirection(ctx);
                target.setRedirectToUiScreen(screenUri.toASCIIString());
                ctx.setLastRedirection(target);
            }
        );
    }

    private void setDestinationUriInContext(DelegateExecution execution, String destinationUri) {
        ContextUtil.getAndUpdateContext(
            execution,
            (BaseContext ctx) -> {
                LastRedirectionTarget target = getOrCreateLastRedirection(ctx);
                target.setRedirectTo(destinationUri);
                ctx.setLastRedirection(target);
            }
        );
    }

    private LastRedirectionTarget getOrCreateLastRedirection(BaseContext ctx) {
        LastRedirectionTarget target = null == ctx.getLastRedirection() ? new LastRedirectionTarget() : ctx.getLastRedirection();
        target.setRequestScoped(ctx.getRequestScoped());
        return target;
    }
}
