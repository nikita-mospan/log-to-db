package mospan.log_to_db.aspectj;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface LogToDb {
    boolean suppressLogArgs() default false;
    boolean suppressLogResult() default false;
}
