package life.wellnara.controller;

import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.web.servlet.error.ErrorController;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * Renders application-styled error pages instead of the default Whitelabel page.
 *
 * <p>Handles every error forwarded to {@code /error} by the servlet container
 * (404, 400, 403 raised outside the security filter, 500, etc.). The security
 * filter chain already redirects unauthenticated users to the login page and
 * forbidden-but-authenticated users to their home; this controller covers the
 * remaining container-level errors so the user never sees a raw stack trace.
 */
@Controller
public class AppErrorController implements ErrorController {

    private final MessageSource messageSource;

    /**
     * Creates the application error controller.
     *
     * @param messageSource resolver of localized user-facing messages
     */
    public AppErrorController(MessageSource messageSource) {
        this.messageSource = messageSource;
    }

    private String msg(String key) {
        return messageSource.getMessage(key, null, LocaleContextHolder.getLocale());
    }

    /**
     * Resolves the error view and exposes a safe, user-facing status message.
     *
     * @param request current request carrying servlet error attributes
     * @param model   MVC model
     * @return error view name
     */
    @RequestMapping("/error")
    public String handleError(HttpServletRequest request, Model model) {
        HttpStatus status = resolveStatus(request);

        model.addAttribute("statusCode", status.value());
        model.addAttribute("statusReason", messageSource.getMessage(
                "error.status." + status.value(), null, status.getReasonPhrase(), LocaleContextHolder.getLocale()));
        model.addAttribute("message", userMessageFor(status));

        return "error";
    }

    private HttpStatus resolveStatus(HttpServletRequest request) {
        Object code = request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE);

        if (code == null) {
            return HttpStatus.INTERNAL_SERVER_ERROR;
        }

        try {
            return HttpStatus.valueOf(Integer.parseInt(code.toString()));
        } catch (IllegalArgumentException exception) {
            return HttpStatus.INTERNAL_SERVER_ERROR;
        }
    }

    private String userMessageFor(HttpStatus status) {
        if (status == HttpStatus.NOT_FOUND) {
            return msg("error.page.notFound");
        }
        if (status == HttpStatus.FORBIDDEN) {
            return msg("error.page.forbidden");
        }
        if (status == HttpStatus.BAD_REQUEST) {
            return msg("error.page.badRequest");
        }
        return msg("error.page.generic");
    }
}
