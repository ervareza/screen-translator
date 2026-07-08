package com.lovanka.screentranslator

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.google.mlkit.nl.languageid.LanguageIdentification
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.TranslatorOptions
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import com.google.mlkit.vision.text.japanese.JapaneseTextRecognizerOptions
import com.google.mlkit.vision.text.korean.KoreanTextRecognizerOptions
import com.google.mlkit.vision.text.latin.TextRecognizerOptions

class TranslationEngine(private val context: Context) {

    private val overlayManager = OverlayManager(context)
    private val config = ConfigManager(context)
    private val languageIdentifier = LanguageIdentification.getClient()
    
    // OCR Clients
    private val jpRecognizer = TextRecognition.getClient(JapaneseTextRecognizerOptions.Builder().build())
    private val krRecognizer = TextRecognition.getClient(KoreanTextRecognizerOptions.Builder().build())
    private val cnRecognizer = TextRecognition.getClient(ChineseTextRecognizerOptions.Builder().build())
    private val enRecognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    fun processImage(bitmap: Bitmap) {
        val image = InputImage.fromBitmap(bitmap, 0)

        // For true auto-detect, we can run the most common manga languages
        // In a real production app, we might run them in parallel and use Language ID to pick the best.
        // For efficiency in this MVP, we will try Japanese first (most common for manga),
        // then Korean if Japanese fails to find meaningful text.
        
        jpRecognizer.process(image)
            .addOnSuccessListener { text ->
                if (text.text.isNotBlank()) {
                    identifyAndTranslate(text)
                } else {
                    // Try Korean if Japanese found nothing
                    krRecognizer.process(image).addOnSuccessListener { krText ->
                        if (krText.text.isNotBlank()) identifyAndTranslate(krText)
                    }
                }
            }
            .addOnFailureListener { e ->
                Log.e("Translator", "OCR Failed", e)
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
        val targetLangCode = config.targetLanguage // e.g. "id"
        
        val options = TranslatorOptions.Builder()
            .setSourceLanguage(sourceLangCode)
            .setTargetLanguage(targetLangCode)
            .build()
            
        val translator = Translation.getClient(options)

        // Ensure model is downloaded
        translator.downloadModelIfNeeded()
            .addOnSuccessListener {
                // Translate each block individually to keep bounding boxes
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
}
