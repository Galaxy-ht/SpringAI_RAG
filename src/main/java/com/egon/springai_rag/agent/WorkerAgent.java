package com.egon.springai_rag.agent;

import org.springframework.beans.factory.annotation.Qualifier;

import java.lang.annotation.*;

/**
 * 标记 Worker Agent 的限定注解。
 *
 * <p>Worker Agent 是执行具体任务的 Agent（ReAct、Reflection、PlanAndExecute），
 * 与 Meta Agent（Router、Chain、Orchestrator）区分开来。
 *
 * <p>用途：解决 {@code Map<String, AdvisorAgent>} 注入时的循环依赖问题 ——
 * Meta Agent 只需要注入 Worker Agent，不需要注入其他 Meta Agent。
 */
@Qualifier
@Target({ElementType.TYPE, ElementType.PARAMETER, ElementType.METHOD, ElementType.FIELD})
@Retention(RetentionPolicy.RUNTIME)
public @interface WorkerAgent {
}