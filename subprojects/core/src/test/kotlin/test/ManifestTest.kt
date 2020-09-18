package test

import com.handtruth.mc.mcsman.server.bundle.Bundles
import kotlin.test.Test

const val instrA = """
<manifest xmlns="http://mcsman.mc.handtruth.com/manifest" group="com.example" artifact="sample" version="0.0.1">
	<module class="com.example.module.Example">
		<after>
			<item>
				mcsman
			</item>
		</after>
	</module>
	<service class="com.example.service.ExampleService"/>
</manifest>
"""

const val instrB = """
<manifest xmlns="http://mcsman.mc.handtruth.com/manifest" group="com.example" artifact="sample" version="0.0.1">
	<module>
		<artifact class="client" type="maven" platform="jvm" uri="mvn:sample/sample-client"/>
	</module>
</manifest>
"""

class ManifestTest {

    @Test
    fun ohh() {
        println(Bundles.mergeManifests(instrA, instrB))
    }
}
