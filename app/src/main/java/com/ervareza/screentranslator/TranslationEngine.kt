package com.ervareza.screentranslator

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.google.mlkit.nl.languageid.LanguageIdentification
import com.google.mlkit.nl.languageid.LanguageIdentifier
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.Translator
import com.google.mlkit.nl.translate.TranslatorOptions
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import com.google.mlkit.vision.text.devanagari.DevanagariTextRecognizerOptions
import com.google.mlkit.vision.text.japanese.JapaneseTextRecognizerOptions
import com.google.mlkit.vision.text.korean.KoreanTextRecognizerOptions
import com.google.mlkit.vision.text.latin.TextRecognizerOptions

class TranslationEngine(private val context: Context) {

    private val overlayManager = OverlayManager(context)
    private val config = ConfigManager(context)
    private val languageIdentifier: LanguageIdentifier = LanguageIdentification.getClient()

    // ISSUE-004 FIX: Cache recognizers instead of creating new ones per call
    private val recognizerCache = mutableMapOf<String, TextRecognizer>()

    // ISSUE-003 FIX: Cache translators per language pair to avoid per-block creation
    private val translatorCache = mutableMapOf<String, Translator>()

    private fun getRecognizer(code: String): TextRecognizer {
        return recognizerCache.getOrPut(code) {
            when (code) {
                "ja" -> TextRecognition.getClient(JapaneseTextRecognizerOptions.Builder().build())
                "ko" -> TextRecognition.getClient(KoreanTextRecognizerOptions.Builder().build())
                "zh" -> TextRecognition.getClient(ChineseTextRecognizerOptions.Builder().build())
                "hi" -> TextRecognition.getClient(DevanagariTextRecognizerOptions.Builder().build())
                else -> TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
            }
        }
    }

    private fun getTranslator(sourceLang: String, targetLang: String): Translator {
        val key = "${sourceLang}_${targetLang}"
        return translatorCache.getOrPut(key) {
            val options = TranslatorOptions.Builder()
                .setSourceLanguage(sourceLang)
                .setTargetLanguage(targetLang)
                .build()
            Translation.getClient(options)
        }
    }

    fun processImage(bitmap: Bitmap) {
        val image = InputImage.fromBitmap(bitmap, 0)

        if (config.sourceLanguage == "auto") {
            val supportedCodes = listOf("ja", "ko", "zh", "hi", "en")
            val installedCodes = supportedCodes.filter { config.isModelInstalled(it) }

            if (installedCodes.isEmpty()) {
                Log.e("Translator", "Auto-detect failed: No OCR models are installed!")
                return
            }
            runFallbackChain(image, installedCodes, 0)
        } else {
            val recognizer = getRecognizer(config.sourceLanguage)
            recognizer.process(image).addOnSuccessListener { text ->
                if (text.text.isNotBlank()) identifyAndTranslate(text)
            }.addOnFailureListener { e ->
                Log.e("Translator", "OCR Failed", e)
            }
        }
    }

    private fun runFallbackChain(image: InputImage, codes: List<String>, index: Int) {
        if (index >= codes.size) return

        val recognizer = getRecognizer(codes[index])
        recognizer.process(image).addOnSuccessListener { text ->
            if (text.text.isNotBlank()) {
                identifyAndTranslate(text)
            } else {
                runFallbackChain(image, codes, index + 1)
            }
        }.addOnFailureListener {
            runFallbackChain(image, codes, index + 1)
        }
    }

    private fun identifyAndTranslate(visionText: Text) {
        val fullText = visionText.text
        languageIdentifier.identifyLanguage(fullText)
            .addOnSuccessListener { languageCode ->
                if (languageCode != "und") {
                    Log.d("Translator", "Detected language: $languageCode")
                    translateBlocks(visionText, languageCode)
                }
            }
    }

    private fun translateBlocks(visionText: Text, sourceLangCode: String) {
        val targetLangCode = config.targetLanguage

        // ISSUE-003 FIX: Reuse a single translator for the entire batch
        val translator = getTranslator(sourceLangCode, targetLangCode)

        translator.downloadModelIfNeeded()
            .addOnSuccessListener {
                for (block in visionText.textBlocks) {
                    translator.translate(block.text)
                        .addOnSuccessListener { translatedText ->
                            block.boundingBox?.let { rect ->
                                overlayManager.drawTranslationBubble(translatedText, rect)
                            }
                        }
                }
            }
            .addOnFailureListener {
                Log.e("Translator", "Model download failed")
            }
    }

    // ISSUE-010 FIX: Clean up all resources
    fun close() {
        languageIdentifier.close()
        recognizerCache.values.forEach { it.close() }
        recognizerCache.clear()
        translatorCache.values.forEach { it.close() }
        translatorCache.clear()
        overlayManager.clearOverlays()
    }
    
    fun clearOverlays() {
        overlayManager.clearOverlays()
    }
}
