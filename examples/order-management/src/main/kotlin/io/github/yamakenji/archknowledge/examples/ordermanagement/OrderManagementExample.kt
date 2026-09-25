package io.github.yamakenji.archknowledge.examples.ordermanagement

import io.github.yamakenji.archknowledge.core.graph.InMemoryConceptGraph
import io.github.yamakenji.archknowledge.core.model.Concept
import io.github.yamakenji.archknowledge.core.model.ConceptId
import io.github.yamakenji.archknowledge.core.model.ConceptTypeId
import io.github.yamakenji.archknowledge.core.model.Relation
import io.github.yamakenji.archknowledge.core.model.RelationId
import io.github.yamakenji.archknowledge.core.model.RelationTypeId
import io.github.yamakenji.archknowledge.profiles.businesssoftware.BusinessSoftwareProfile.APPLICATION
import io.github.yamakenji.archknowledge.profiles.businesssoftware.BusinessSoftwareProfile.BOUNDED_CONTEXT
import io.github.yamakenji.archknowledge.profiles.businesssoftware.BusinessSoftwareProfile.BUSINESS_PROCESS
import io.github.yamakenji.archknowledge.profiles.businesssoftware.BusinessSoftwareProfile.CAPABILITY
import io.github.yamakenji.archknowledge.profiles.businesssoftware.BusinessSoftwareProfile.IMPLEMENTS
import io.github.yamakenji.archknowledge.profiles.businesssoftware.BusinessSoftwareProfile.REALIZED_BY
import io.github.yamakenji.archknowledge.profiles.businesssoftware.BusinessSoftwareProfile.SUPPORTED_BY

/**
 * Fictional Order Management enterprise model. Expected results are documented in this module's README.
 *
 * The HR chain is deliberately disconnected from Order Management.
 */
object OrderManagementExample {
    const val PROVENANCE = "Sample enterprise model v0.1 (fictional)"

    private fun concept(id: String, type: ConceptTypeId, name: String) =
        Concept(ConceptId(id), type, name, provenance = PROVENANCE)

    private fun relation(id: String, source: String, type: RelationTypeId, target: String) =
        Relation(RelationId(id), type, ConceptId(source), ConceptId(target), provenance = PROVENANCE)

    val concepts = listOf(
        concept("cap-order-management", CAPABILITY, "受注管理"),
        concept("bp-order-intake", BUSINESS_PROCESS, "注文受付"),
        concept("bp-order-fulfillment", BUSINESS_PROCESS, "出荷手配"),
        concept("app-oms", APPLICATION, "Order Management System"),
        concept("app-crm", APPLICATION, "Customer Relationship Management"),
        concept("app-wms", APPLICATION, "Warehouse Management System"),
        concept("bc-order", BOUNDED_CONTEXT, "Order Context"),
        concept("bc-customer", BOUNDED_CONTEXT, "Customer Context"),
        concept("bc-shipping", BOUNDED_CONTEXT, "Shipping Context"),

        concept("cap-hr-management", CAPABILITY, "人事管理"),
        concept("bp-payroll", BUSINESS_PROCESS, "給与計算"),
        concept("app-hris", APPLICATION, "HR Information System"),
        concept("bc-payroll", BOUNDED_CONTEXT, "Payroll Context"),
    )

    val relations = listOf(
        relation("rel-001", "cap-order-management", REALIZED_BY, "bp-order-intake"),
        relation("rel-002", "cap-order-management", REALIZED_BY, "bp-order-fulfillment"),
        relation("rel-003", "bp-order-intake", SUPPORTED_BY, "app-oms"),
        relation("rel-004", "bp-order-intake", SUPPORTED_BY, "app-crm"),
        relation("rel-005", "bp-order-fulfillment", SUPPORTED_BY, "app-oms"),
        relation("rel-006", "bp-order-fulfillment", SUPPORTED_BY, "app-wms"),
        relation("rel-007", "app-oms", IMPLEMENTS, "bc-order"),
        relation("rel-008", "app-crm", IMPLEMENTS, "bc-customer"),
        relation("rel-009", "app-wms", IMPLEMENTS, "bc-shipping"),

        relation("rel-101", "cap-hr-management", REALIZED_BY, "bp-payroll"),
        relation("rel-102", "bp-payroll", SUPPORTED_BY, "app-hris"),
        relation("rel-103", "app-hris", IMPLEMENTS, "bc-payroll"),
    )

    fun graph() = InMemoryConceptGraph(concepts, relations)
}
