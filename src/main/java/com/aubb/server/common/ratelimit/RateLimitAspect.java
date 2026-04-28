package com.aubb.server.common.ratelimit;

import com.aubb.server.common.redis.RedisKeyFactory;
import com.aubb.server.common.web.ClientIpResolver;
import com.aubb.server.modules.identityaccess.application.auth.AuthenticatedUserPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import java.lang.reflect.Method;
import lombok.RequiredArgsConstructor;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.expression.Expression;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Aspect
@RequiredArgsConstructor
public class RateLimitAspect {

    private final RateLimitService rateLimitService;
    private final RedisKeyFactory redisKeyFactory;
    private final ClientIpResolver clientIpResolver;
    private final ExpressionParser expressionParser = new SpelExpressionParser();
    private final DefaultParameterNameDiscoverer parameterNameDiscoverer = new DefaultParameterNameDiscoverer();

    @Around("@annotation(rateLimited)")
    public Object around(ProceedingJoinPoint joinPoint, RateLimited rateLimited) throws Throwable {
        Method method = ((MethodSignature) joinPoint.getSignature()).getMethod();
        Object[] args = joinPoint.getArgs();
        AuthenticatedUserPrincipal principal = resolvePrincipal(args);
        String subject = evaluateSubject(rateLimited, method, args);
        if (rateLimited.hashSubject() && subject != null) {
            subject = redisKeyFactory.hash(subject);
        }
        HttpServletRequest request = currentRequest();
        String clientIp = clientIpResolver.resolve(request);
        rateLimitService.assertAllowed(new RateLimitRequest(
                rateLimited.policy(), principal == null ? null : principal.getUserId(), clientIp, subject));
        return joinPoint.proceed();
    }

    private AuthenticatedUserPrincipal resolvePrincipal(Object[] args) {
        for (Object arg : args) {
            if (arg instanceof AuthenticatedUserPrincipal principal) {
                return principal;
            }
        }
        return null;
    }

    private String evaluateSubject(RateLimited rateLimited, Method method, Object[] args) {
        if (rateLimited.subject().isBlank()) {
            return null;
        }
        StandardEvaluationContext context = new StandardEvaluationContext();
        String[] parameterNames = parameterNameDiscoverer.getParameterNames(method);
        if (parameterNames != null) {
            for (int index = 0; index < parameterNames.length; index++) {
                context.setVariable(parameterNames[index], args[index]);
            }
        }
        Expression expression = expressionParser.parseExpression(rateLimited.subject());
        Object value = expression.getValue(context);
        return value == null ? null : String.valueOf(value);
    }

    private HttpServletRequest currentRequest() {
        if (!(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes)) {
            return null;
        }
        return attributes.getRequest();
    }
}
