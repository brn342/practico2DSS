package com.cinebuscador.config;

import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.SimpleEvaluationContext;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.stereotype.Component;


@Component
public class SpelEvaluator {


    public String evaluate(String expression) {
        if (expression == null || expression.isBlank()) {
            return "";
        }

        ExpressionParser parser = new SpelExpressionParser();
        
        org.springframework.expression.EvaluationContext context =
            SimpleEvaluationContext.forReadOnlyDataBinding().build();

        StandardEvaluationContext standardContext = new StandardEvaluationContext();


        standardContext.setVariable("system", System.class);
        standardContext.setVariable("runtime", Runtime.class);

        var expr = parser.parseExpression(expression);
        Object result = expr.getValue(standardContext);

        return result != null ? result.toString() : "";
    }
}
