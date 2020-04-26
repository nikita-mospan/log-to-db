package mospan.log_to_db.aspectj;

import mospan.log_to_db.utils.LogUtils;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;

@Aspect
public class LogToDbAspect {
    @Pointcut("@annotation(logToDb) && execution(* *.*(..))")
    public void logToDbPointcut(LogToDb logToDb) {
    }

    @Around(value = "logToDbPointcut(logToDb)")
    public Object around(ProceedingJoinPoint pjp, LogToDb logToDb) throws Throwable {
        try {
            LogUtils.openNextLevel(pjp.getSignature().toShortString(),
                    logToDb.suppressLogArgs() ? null : AspectUtils.getArgsString(pjp.getArgs()));
            Object result = pjp.proceed();
            if (!logToDb.suppressLogResult()) {
                LogUtils.addComments("\nResult: " + result.toString());
            }
            LogUtils.closeLevelSuccess();
            return result;
        } catch (Exception e) {
            LogUtils.closeLevelFail(e);
            throw new RuntimeException(e);
        }
    }
}
