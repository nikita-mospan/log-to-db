package mospan.log_to_db.aspectj;

import java.util.Arrays;
import java.util.stream.Collectors;

public class AspectUtils {

    public AspectUtils() {
        throw new RuntimeException("Class AspectUtils contains static methods and must not be instantiated!");
    }

    static String getArgsString(Object[] args) {
        return "Arguments: " + Arrays.stream(args)
                .map(arg -> {
                    if (arg.getClass().isArray()) {
                        return Arrays.toString((Object[]) arg);
                    } else {
                        return arg.toString();
                    }
                })
                .collect(Collectors.joining(", "));
    }

}
