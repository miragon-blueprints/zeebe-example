package io.miragon.common.test.assertions

import io.camunda.process.test.api.assertions.ProcessInstanceAssert
import io.miragon.bpmn.runtime.FlowNode
import io.miragon.bpmn.runtime.path.ProcessPath

fun ProcessInstanceAssert.hasCompletedElements(vararg elements: FlowNode): ProcessInstanceAssert =
    hasCompletedElements(*elements.map { it.id.value }.toTypedArray())

fun ProcessInstanceAssert.hasCompletedElementsInOrder(path: ProcessPath<*>): ProcessInstanceAssert =
    hasCompletedElementsInOrder(*path.ids.toTypedArray())

fun ProcessInstanceAssert.hasCompletedElements(path: ProcessPath<*>): ProcessInstanceAssert =
    hasCompletedElements(*path.distinctIds.toTypedArray())
