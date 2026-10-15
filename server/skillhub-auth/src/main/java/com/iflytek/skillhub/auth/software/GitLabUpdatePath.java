package com.iflytek.skillhub.auth.software;

import com.iflytek.skillhub.domain.software.SoftwareUpdateRules;
import jakarta.servlet.http.HttpServletRequest;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.regex.Pattern;

/** Split route delimiters before decoding data coordinates; never apply form decoding to paths. */
public final class GitLabUpdatePath {
    private static final Pattern API = Pattern.compile("^/api/v4/projects/([^/]+)/repository/(?:tags|files/([^/]+)/raw)$");
    private GitLabUpdatePath() { }
    public static String requestPath(HttpServletRequest request) {
        String path = request.getRequestURI();
        String context = request.getContextPath();
        if (context != null && !context.isEmpty() && path.startsWith(context + "/")) path = path.substring(context.length());
        return path;
    }
    public static Optional<Route> parse(HttpServletRequest request) {
        if (!"GET".equals(request.getMethod()) && !"HEAD".equals(request.getMethod())) return Optional.empty();
        var match = API.matcher(requestPath(request));
        if (!match.matches()) return Optional.empty();
        try {
            String project = decode(match.group(1));
            if (!project.matches("[A-Za-z0-9._-]+(?:/[A-Za-z0-9._-]+)+")) return Optional.empty();
            String file = match.group(2) == null ? null : decode(match.group(2));
            return Optional.of(new Route(project, file));
        } catch (RuntimeException invalid) { return Optional.empty(); }
    }
    public static String decode(String raw) {
        try {
            var bytes = new ByteArrayOutputStream();
            for (int index = 0; index < raw.length();) {
                if (raw.charAt(index) == '%') {
                    bytes.write(Integer.parseInt(raw.substring(index + 1, index + 3), 16)); index += 3;
                } else {
                    int point = raw.codePointAt(index);
                    bytes.writeBytes(new String(Character.toChars(point)).getBytes(StandardCharsets.UTF_8));
                    index += Character.charCount(point);
                }
            }
            String value = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes.toByteArray())).toString();
            SoftwareUpdateRules.path(value);
            if (value.contains(";")) throw new IllegalArgumentException("invalid coordinate");
            return value;
        } catch (Exception invalid) { throw new IllegalArgumentException("invalid update coordinate"); }
    }
    public record Route(String project, String file) { public boolean tags() { return file == null; } }
}
