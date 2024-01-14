package info.hellovass.gradle.plugin.embeddable.extension

import org.gradle.api.model.ObjectFactory
import org.gradle.kotlin.dsl.property
import javax.inject.Inject

abstract class EmbeddableExtension @Inject constructor(factory: ObjectFactory) {

    /**
     *
     */
    val yourkitJar = factory.property<String>().convention("")

    /**
     *
     */
    val yourkitAgent = factory.property<String>().convention("")

    /**
     *
     */
    val allocationInstrument = factory.property<String>().convention("")
}