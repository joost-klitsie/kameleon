package dev.klitsie.kameleon

import org.gradle.api.attributes.Attribute
import org.gradle.api.attributes.AttributeCompatibilityRule
import org.gradle.api.attributes.AttributeDisambiguationRule
import org.gradle.api.attributes.CompatibilityCheckDetails
import org.gradle.api.attributes.MultipleCandidatesDetails
import javax.inject.Inject

object FlavorAttribute {
    val FLAVOR_ATTRIBUTE: Attribute<String> = Attribute.of("dev.klitsie.kameleon.flavor", String::class.java)

}

class FlavorDisambiguationRule @Inject constructor(
    private val defaultFlavor: String,
) : AttributeDisambiguationRule<String> {

    override fun execute(details: MultipleCandidatesDetails<String>) {
        val candidates = details.candidateValues
        if (candidates.contains(defaultFlavor)) {
            details.closestMatch(defaultFlavor)
        } else if (candidates.isNotEmpty()) {
            details.closestMatch(candidates.first())
        }
    }
}

class CaseInsensitiveCompatibilityRule : AttributeCompatibilityRule<String> {
    override fun execute(details: CompatibilityCheckDetails<String>) {
        val consumer = details.consumerValue
        val producer = details.producerValue
        if (consumer != null && producer != null && consumer.equals(producer, ignoreCase = true)) {
            details.compatible()
        }
    }
}
