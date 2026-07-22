"""
Qwen3-TTS API Server
====================
Exposes an OpenAI-compatible POST /v1/audio/speech endpoint
for Qwen3-TTS-12Hz-0.6B-CustomVoice text-to-speech synthesis.

Usage:
    pip install transformers fastapi uvicorn soundfile accelerate
    python tts_server.py          # starts on port 8001
"""

import io
import logging
import torch
import soundfile as sf
from fastapi import FastAPI, HTTPException
from fastapi.responses import Response
from pydantic import BaseModel, Field
from typing import Optional
import uvicorn

logging.basicConfig(level=logging.INFO)
logger = logging.getLogger("tts_server")

app = FastAPI(title="Qwen3-TTS API", version="1.0.0")

MODEL_NAME = "Qwen/Qwen3-TTS-12Hz-0.6B-CustomVoice"

# Lazy-loaded model
model = None
processor = None


class TtsRequest(BaseModel):
    model: str = Field(default=MODEL_NAME, description="Model name")
    input: str = Field(..., description="Text to synthesize")
    voice: Optional[str] = Field(default="default", description="Voice name")
    response_format: Optional[str] = Field(default="wav", description="Audio format")
    speed: Optional[float] = Field(default=1.0, ge=0.5, le=2.0, description="Speech speed multiplier")


def load_model():
    """Load the model on first request."""
    global model, processor
    if model is not None:
        return

    logger.info("Loading Qwen3-TTS model...")
    from transformers import AutoProcessor, Qwen2AudioForConditionalGeneration

    device = "cuda" if torch.cuda.is_available() else "cpu"
    logger.info(f"Using device: {device}")

    processor = AutoProcessor.from_pretrained(MODEL_NAME, trust_remote_code=True)
    model = Qwen2AudioForConditionalGeneration.from_pretrained(
        MODEL_NAME,
        trust_remote_code=True,
        torch_dtype=torch.float16 if device == "cuda" else torch.float32,
        device_map="auto" if device == "cuda" else None,
    )
    model.to(device)
    model.eval()
    logger.info("Model loaded successfully")


@app.post("/v1/audio/speech")
async def tts_speech(req: TtsRequest):
    """OpenAI-compatible TTS endpoint. Returns WAV audio bytes."""
    try:
        load_model()

        device = next(model.parameters()).device

        messages = [
            {
                "role": "user",
                "content": [
                    {"type": "text", "text": req.input},
                ],
            }
        ]

        prompt = processor.apply_chat_template(
            messages, tokenize=False, add_generation_prompt=True
        )

        inputs = processor(
            text=prompt,
            padding=True,
            return_tensors="pt",
        )

        # Move inputs to the same device as the model
        inputs = {k: v.to(device) for k, v in inputs.items()}

        with torch.no_grad():
            generated_ids = model.generate(
                **inputs,
                max_new_tokens=512,
                do_sample=True,
                temperature=0.7,
            )

        # Decode the generated audio tokens
        audio_values = generated_ids  # In Qwen2Audio, this returns audio tokens
        # The processor handles decoding
        audio = processor.decode_audio(generated_ids[0].cpu().numpy())

        # Apply speed adjustment
        if req.speed != 1.0 and audio is not None:
            import numpy as np
            original_rate = processor.feature_extractor.sampling_rate if hasattr(processor, 'feature_extractor') else 24000
            # Simple speed change via resampling
            new_rate = int(original_rate * req.speed)
            # Use soundfile to apply speed change by resampling
            import resampy
            audio = resampy.resample(audio, original_rate, new_rate)

        if audio is None:
            raise HTTPException(status_code=500, detail="TTS synthesis failed: no audio output")

        # Determine sample rate
        sample_rate = processor.feature_extractor.sampling_rate if hasattr(processor, 'feature_extractor') else 24000
        if req.speed != 1.0:
            sample_rate = int(sample_rate * req.speed)

        # Write WAV to bytes
        buf = io.BytesIO()
        sf.write(buf, audio, samplerate=sample_rate, format="WAV")
        wav_bytes = buf.getvalue()

        logger.info(
            f"TTS synthesized: text_len={len(req.input)}, "
            f"voice={req.voice}, speed={req.speed}, "
            f"audio_size={len(wav_bytes)} bytes"
        )

        return Response(content=wav_bytes, media_type="audio/wav")

    except HTTPException:
        raise
    except Exception as e:
        logger.exception("TTS synthesis error")
        raise HTTPException(status_code=500, detail=str(e))


@app.get("/health")
async def health():
    """Health check endpoint."""
    return {"status": "ok", "model_loaded": model is not None}


if __name__ == "__main__":
    uvicorn.run(app, host="0.0.0.0", port=8001)