package life.wellnara.controller;

import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.web.servlet.error.ErrorController;
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
        model.addAttribute("statusReason", status.getReasonPhrase());
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
            return "Page not found.";
        }
        if (status == HttpStatus.FORBIDDEN) {
            return "You do not have access to this page.";
        }
        if (status == HttpStatus.BAD_REQUEST) {
            return "The request could not be processed.";
        }
        return "Something went wrong. Please try again later.";
    }
}
