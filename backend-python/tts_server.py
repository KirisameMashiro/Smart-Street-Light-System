"""
Qwen3-TTS API Server
====================
Exposes an OpenAI-compatible POST /v1/audio/speech endpoint
for Qwen3-TTS-12Hz-0.6B-CustomVoice text-to-speech synthesis.

Usage:
    pip install qwen-tts fastapi uvicorn soundfile accelerate torch
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


class TtsRequest(BaseModel):
    model: str = Field(default=MODEL_NAME, description="Model name")
    input: str = Field(..., description="Text to synthesize")
    voice: Optional[str] = Field(default="Vivian", description="Speaker name: Vivian, Serena, Uncle_Fu, Dylan, Eric, Ryan, Aiden, Ono_Anna, Sohee")
    instruct: Optional[str] = Field(default=None, description="Instruction for emotion/style (e.g., '用特别愤怒的语气说')")
    language: Optional[str] = Field(default="Chinese", description="Language: Chinese, English, Japanese, Korean")
    response_format: Optional[str] = Field(default="wav", description="Audio format")
    speed: Optional[float] = Field(default=1.0, ge=0.5, le=2.0, description="Speech speed multiplier (not fully supported)")


def load_model():
    """Load the model on first request."""
    global model
    if model is not None:
        return

    logger.info("Loading Qwen3-TTS model...")
    from qwen_tts import Qwen3TTSModel

    # Check for CUDA
    device = "cuda:0" if torch.cuda.is_available() else "cpu"
    logger.info(f"Using device: {device}")

    # Use bfloat16 if CUDA available, else float32
    dtype = torch.bfloat16 if torch.cuda.is_available() else torch.float32
    
    # attn_implementation only for CUDA
    attn_kwargs = {}
    if torch.cuda.is_available():
        attn_kwargs["attn_implementation"] = "flash_attention_2"

    model = Qwen3TTSModel.from_pretrained(
        MODEL_NAME,
        device_map=device,
        dtype=dtype,
        **attn_kwargs
    )
    
    logger.info("Model loaded successfully")


@app.post("/v1/audio/speech")
async def tts_speech(req: TtsRequest):
    """OpenAI-compatible TTS endpoint. Returns WAV audio bytes."""
    try:
        load_model()

        # Generate speech
        wavs, sr = model.generate_custom_voice(
            text=req.input,
            language=req.language,
            speaker=req.voice,
            instruct=req.instruct if req.instruct else None,
        )

        if not wavs or len(wavs) == 0:
            raise HTTPException(status_code=500, detail="TTS synthesis failed: no audio output")

        # Get the first audio channel
        audio = wavs[0] if isinstance(wavs, list) else wavs

        # Convert to numpy if tensor
        if torch.is_tensor(audio):
            audio = audio.cpu().numpy()

        # Speed adjustment (simple resampling - changes pitch too)
        if req.speed != 1.0 and audio is not None:
            import resampy
            new_sr = int(sr * req.speed)
            audio = resampy.resample(audio, sr, new_sr)
            sr = new_sr

        # Write WAV to bytes
        buf = io.BytesIO()
        sf.write(buf, audio, samplerate=sr, format="WAV")
        wav_bytes = buf.getvalue()

        logger.info(
            f"TTS synthesized: text_len={len(req.input)}, "
            f"voice={req.voice}, language={req.language}, "
            f"instruct={req.instruct}, audio_size={len(wav_bytes)} bytes"
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