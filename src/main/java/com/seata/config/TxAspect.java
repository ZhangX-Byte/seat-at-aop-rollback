package com.seata.config;

import com.github.dozermapper.core.Mapper;
import com.seata.dto.Response;
import io.seata.core.context.RootContext;
import io.seata.core.exception.TransactionException;
import io.seata.tm.api.GlobalTransactionContext;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.*;
import org.springframework.stereotype.Component;
import org.springframework.validation.BindException;
import org.springframework.web.bind.MethodArgumentNotValidException;

import javax.validation.ConstraintViolationException;
import java.util.Arrays;

/**
 * @author Zhang_Xiang
 * @since 2021/2/22 17:36:16
 */
@Aspect
@Component
@Slf4j
public class TxAspect {

    private static final String ERROR_CODE = "-1";

    private final Mapper mapper;

    public TxAspect(Mapper mapper) {
        this.mapper = mapper;
    }

    @Pointcut("execution(public * *(..))")
    public void publicMethod() {
    }

    @Pointcut("bean(*ServiceImpl)")
    private void serviceImpls() {
    }

    @Pointcut("@annotation(org.springframework.transaction.annotation.Transactional)")
    private void transactional() {
    }

    @Pointcut("bean(*Controller)")
    private void discoveryActions() {
    }

    @Pointcut("@annotation(org.springframework.web.bind.annotation.ExceptionHandler))")
    private void validatedException() {
    }

    @Before(value = "validatedException()")
    public void beforeValidate(JoinPoint joinPoint) throws TransactionException {
        Object[] args = joinPoint.getArgs();
        if (args == null || Arrays.stream(args).count() == 0) {
            return;
        }
        Exception e = (Exception) args[0];
        if (e instanceof MethodArgumentNotValidException || e instanceof BindException || e instanceof
                ConstraintViolationException) {
            globalRollback();
        }
    }

    @AfterThrowing(throwing = "e", pointcut = "publicMethod()&&serviceImpls()&&transactional()")
    public void doRecoveryMethods(Throwable e) throws TransactionException {
        log.info("===method throw===:{}", e.getMessage());
        globalRollback();
    }

    @AfterReturning(value = "publicMethod()&&discoveryActions()", returning = "result")
    public void afterReturning(Object result) throws TransactionException {
        log.info("===method finished===:{}", result);

        Response resp = mapper.map(result, Response.class);
        if (resp.getFailed() != null && resp.getFailed()) {
            globalRollback();
        }
        if (resp.getCode() != null && ERROR_CODE.equals(resp.getCode())) {
            globalRollback();
        }
    }

    //region private methods

    private void globalRollback() throws TransactionException {
        if (!StringUtils.isBlank(RootContext.getXID())) {
            log.info("===xid===:{}", RootContext.getXID());
            GlobalTransactionContext.reload(RootContext.getXID()).rollback();
        }
    }

    //endregion
}
