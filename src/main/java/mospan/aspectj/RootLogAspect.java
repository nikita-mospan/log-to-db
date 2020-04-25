package mospan.aspectj;

import mospan.db_log_with_hierarchy.LogUtils;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;

import java.util.Arrays;
import java.util.stream.Collectors;

@Aspect
public class RootLogAspect {
    @Pointcut("@annotation(rootLog) && execution(* *.*(..))")
    public void rootLogPointcut(RootLog rootLog) {
    }

    @Around(value = "rootLogPointcut(rootLog)")
    public Object around(ProceedingJoinPoint pjp, RootLog rootLog) throws Throwable {
        try {
            LogUtils.startLog(pjp.getSignature().toShortString()
                    , "Arguments: " + Arrays.stream(pjp.getArgs()).map(Object::toString).collect(Collectors.joining(", ")));
            Object result = pjp.proceed();
            LogUtils.stopLogSuccess();
            return result;
        } catch (Exception e) {
            LogUtils.stopLogFail(e);
            throw new RuntimeException(e);
        }
    }
}
