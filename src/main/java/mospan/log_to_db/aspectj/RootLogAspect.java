package mospan.log_to_db.aspectj;

import mospan.log_to_db.utils.LogUtils;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;

@Aspect
public class RootLogAspect {
    @Pointcut("@annotation(rootLog) && execution(* *.*(..))")
    public void rootLogPointcut(RootLog rootLog) {
    }

    @Around(value = "rootLogPointcut(rootLog)")
    public Object around(ProceedingJoinPoint pjp, RootLog rootLog) throws Throwable {
        try {
            LogUtils.startLog(pjp.getSignature().toShortString()
                    , rootLog.suppressLogArgs() ? null : AspectUtils.getArgsString(pjp.getArgs()));
            Object result = pjp.proceed();
            LogUtils.stopLogSuccess();
            return result;
        } catch (Exception e) {
            LogUtils.stopLogFail(e);
            throw new RuntimeException(e);
        }
    }
}
