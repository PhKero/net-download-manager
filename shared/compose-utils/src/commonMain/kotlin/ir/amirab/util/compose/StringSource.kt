package ir.amirab.util.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import arrow.core.combine
import ir.amirab.util.compose.localizationmanager.LanguageManager
import ir.amirab.util.compose.localizationmanager.withReplacedArgs
import ir.amirab.util.compose.resources.MyStringResource
import ir.amirab.util.compose.resources.myStringResource

@Immutable
sealed interface StringSource {
    @Composable
    fun rememberString(): String

    @Composable
    fun rememberString(args: Map<String, String>): String
    fun getString(): String
    fun getString(args: Map<String, String>): String

    @Immutable
    data class FromString(
        val value: String,
    ) : StringSource {
        @Composable
        override fun rememberString(): String {
            return value
        }

        @Composable
        override fun rememberString(args: Map<String, String>): String {
            return remember(args) {
                if (args.isEmpty()) {
                    value
                } else {
                    value.withReplacedArgs(args)
                }
            }
        }

        override fun getString(): String {
            return value
        }

        override fun getString(args: Map<String, String>): String {
            return if (args.isEmpty()) {
                value
            } else {
                value.withReplacedArgs(args)
            }
        }
    }

    @Immutable
    data class FromStringResource(
        val value: MyStringResource,
        val extraArgs: Map<String, String> = emptyMap(),
    ) : StringSource {
        @Composable
        override fun rememberString(): String {
            return myStringResource(value, extraArgs)
        }

        @Composable
        override fun rememberString(args: Map<String, String>): String {
            val argList = remember(extraArgs, args) {
                extraArgs.plus(args)
            }
            return if (argList.isEmpty()) {
                myStringResource(value)
            } else {
                myStringResource(value, argList)
            }
        }

        private fun getLanguageManager(): LanguageManager {
            return LanguageManager.instance
        }

        override fun getString(): String {
            return getLanguageManager()
                .getMessage(value.id)
                .withReplacedArgs(extraArgs)
        }

        override fun getString(args: Map<String, String>): String {
            return getLanguageManager()
                .getMessage(value.id)
                .withReplacedArgs(extraArgs.plus(args))
        }
    }

    /**
     * Net Download Manager texts that are not in the properties files yet: English and Arabic side by side.
     * Arabic is the only right-to-left language we ship, so the layout direction tells us which one to show.
     */
    @Immutable
    data class Bilingual(
        val en: String,
        val ar: String,
    ) : StringSource {
        private fun pick(arabic: Boolean) = if (arabic) ar else en
        private fun isArabicNow() = runCatching { LanguageManager.instance.isRtl.value }.getOrDefault(false)

        @Composable
        override fun rememberString(): String {
            return pick(LanguageManager.instance.isRtl.collectAsState().value)
        }

        @Composable
        override fun rememberString(args: Map<String, String>): String {
            return rememberString().withReplacedArgs(args)
        }

        override fun getString(): String {
            return pick(isArabicNow())
        }

        override fun getString(args: Map<String, String>): String {
            return getString().withReplacedArgs(args)
        }
    }

    @Immutable
    data class CombinedStringSource(
        val values: List<StringSource>,
        val separator: String,
    ) : StringSource {
        @Composable
        override fun rememberString(): String {
            return values.map {
                it.rememberString()
            }.joinToString(separator)
        }

        @Composable
        override fun rememberString(args: Map<String, String>): String {
            return values.map {
                it.rememberString(args)
            }.joinToString(separator)
        }

        override fun getString(): String {
            return values.map {
                it.getString()
            }.joinToString(separator)
        }

        override fun getString(args: Map<String, String>): String {
            return values.map {
                it.getString(args)
            }.joinToString(separator)
        }
    }
}

fun MyStringResource.asStringSource(): StringSource {
    return StringSource.FromStringResource(this)
}

fun MyStringResource.asStringSourceWithARgs(args: Map<String, String>): StringSource {
    return StringSource.FromStringResource(this, args)
}

fun String.asStringSource(): StringSource {
    return StringSource.FromString(this)
}

/** English / Arabic text for our own screens */
fun tr(en: String, ar: String): StringSource {
    return StringSource.Bilingual(en, ar)
}

/** same as [tr] but straight to a String, for Text("...") calls */
@Composable
fun trs(en: String, ar: String): String {
    return StringSource.Bilingual(en, ar).rememberString()
}

fun List<StringSource>.combineStringSources(separator: String = ""): StringSource {
    return StringSource.CombinedStringSource(this, separator)
}
