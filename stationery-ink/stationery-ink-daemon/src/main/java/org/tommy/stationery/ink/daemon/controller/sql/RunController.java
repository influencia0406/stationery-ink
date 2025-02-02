package org.tommy.stationery.ink.daemon.controller.sql;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.tommy.stationery.ink.daemon.service.metastore.AuthService;
import org.tommy.stationery.ink.daemon.service.metastore.StatementBuilderService;
import org.tommy.stationery.ink.daemon.util.MultiTenantProxyUtil;
import org.tommy.stationery.ink.daemon.util.SessionUtil;
import org.tommy.stationery.ink.domain.BaseStatement;
import org.tommy.stationery.ink.domain.ResultStatement;
import org.tommy.stationery.ink.domain.SqlResults;
import org.tommy.stationery.ink.domain.cluster.Tenant;
import org.tommy.stationery.ink.domain.meta.Auth;
import org.tommy.stationery.ink.enums.MessageEnum;
import org.tommy.stationery.ink.enums.StatementTypeEnum;
import org.tommy.stationery.ink.exception.InkException;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by kun7788 on 15. 1. 28..
 */
@RestController
@RequestMapping(value = "/sql", method = {RequestMethod.POST, RequestMethod.GET})
public class RunController {
    private static final Logger logger = LoggerFactory.getLogger(RunController.class);

    @Autowired
    AuthService authService;

    @Autowired
    StatementBuilderService statementBuilderService;

    @Autowired
    MultiTenantProxyUtil multiTenantProxyUtil;

    @Autowired
    SessionUtil sessionUtil;

    @RequestMapping("/run")
    public Object run(@RequestParam(value = "sessionId", required = true) String sessionId, @RequestParam(value = "user", required = true) String user, @RequestParam(value = "password", required = true) String password, @RequestParam(value = "sql", required = true) String sql) throws Exception {
        //auth check
        Auth auth = null;
        if (authService.isEnableAuth() == false) {
            auth = authService.superGrantUserAuth();
        } else {
            auth = authService.getInkAuth(new Auth(user, password));
            if (auth == null) {
                throw new InkException(MessageEnum.INVALID_AUTH_INFO);
            }
        }

        if (";".equals(sql.substring(sql.length() - 1, sql.length())) == false) {
            sql+=";";
        }

        //session sql process.
        sql = sessionUtil.getSessionConvSql(sessionId, statementBuilderService.prepare(auth, sql), sql);

        List<ResultStatement> resultStatements = new ArrayList<ResultStatement>();
        try {
            logger.info("sessionId : " + sessionId);
            logger.info("sql : " + sql);

            List<BaseStatement> statements = statementBuilderService.prepare(auth, sql);
            Tenant otherTenant = multiTenantProxyUtil.getOtherTenantInfo(statements);

            //my tenant
            if (otherTenant == null) {
                logger.info("multi_tenant is mine. none proxy");
                resultStatements = statementBuilderService.run(sql, statements);
                return statementBuilderService.FromResultStatements(resultStatements);
            } else {
                //other tenant
                logger.info("multi_tenant is other. proxy start : " + otherTenant.getName() + " : " + otherTenant.getUrl());
                SqlResults sqlResults = multiTenantProxyUtil.proxyCall(otherTenant.getUrl(), user, password, sessionId, sql);
                return sqlResults;
            }

        } catch (Exception ex) {
            String msg = (ex.getMessage() == null ? "UNKNOWN ERROR" : ex.getMessage());
            logger.info("Exception : " + msg);

            ResultStatement resultStatement = new ResultStatement();

            BaseStatement statement = new BaseStatement();
            statement.setType(StatementTypeEnum.RUNTIME);
            statement.setQuery(sql);
            resultStatement.setStatement(statement);
            resultStatement.setErrorMsg(msg);

            resultStatements.add(resultStatement);
        }

        return statementBuilderService.FromResultStatements(resultStatements);
    }
}
