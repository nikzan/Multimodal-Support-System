from flask import Flask, request, jsonify
from flask_cors import CORS
import whisper
import os
import tempfile
import logging

# Configure logging
logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)

app = Flask(__name__)
CORS(app)

# Initialize Whisper model (medium is good balance for M1 Pro)
# model_size options: tiny, base, small, medium, large
# For M1 Pro 16GB: medium is optimal (supports multiple languages including Russian)
logger.info("Loading Whisper model...")
model = whisper.load_model("medium")
logger.info("Whisper model loaded successfully")

@app.route('/health', methods=['GET'])
def health():
    """Health check endpoint"""
    return jsonify({"status": "ok", "model": "medium"}), 200

@app.route('/transcribe', methods=['POST'])
def transcribe():
    """
    Transcribe audio file to text
    
    Expected: multipart/form-data with 'audio' file
    Optional: 'language' parameter (e.g., 'en', 'ru', 'auto')
    
    Returns: {"text": "transcribed text", "language": "detected_language"}
    """
    try:
        # Check if audio file is present
        if 'audio' not in request.files:
            return jsonify({"error": "No audio file provided"}), 400
        
        audio_file = request.files['audio']
        
        if audio_file.filename == '':
            return jsonify({"error": "Empty filename"}), 400
        
        # Get optional language parameter (default: auto-detect)
        language = request.form.get('language', None)
        
        # Save uploaded file temporarily
        with tempfile.NamedTemporaryFile(delete=False, suffix='.wav') as temp_audio:
            audio_file.save(temp_audio.name)
            temp_path = temp_audio.name
        
        try:
            # Transcribe
            logger.info(f"Transcribing audio (language: {language or 'auto-detect'})...")
            result = model.transcribe(
                temp_path,
                language=language,
                fp16=False  # M1 doesn't support FP16, use FP32
            )
            
            full_text = result["text"]
            detected_language = result["language"]
            
            logger.info(f"Transcription completed. Detected language: {detected_language}")
            
            return jsonify({
                "text": full_text.strip(),
                "language": detected_language
            }), 200
            
        finally:
            # Clean up temp file
            if os.path.exists(temp_path):
                os.remove(temp_path)
    
    except Exception as e:
        logger.error(f"Transcription error: {str(e)}", exc_info=True)
        return jsonify({"error": str(e)}), 500

if __name__ == '__main__':
    # Run on port 5001 (Spring Boot is on 8080)
    app.run(host='0.0.0.0', port=5001, debug=True)
