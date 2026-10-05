from fastapi import FastAPI, UploadFile, File, Form, HTTPException
from fastapi.responses import JSONResponse
import io
from PIL import Image
import json
from vlm_service import VLMService
from schemas import VLMResponse

app = FastAPI(title="Local VLM Server")

# Carrega o modelo na inicialização global
vlm = VLMService()

@app.get("/health")
def health():
    return {
        "status": "ok",
        "model_loaded": getattr(vlm, "loaded", False),
        "device": vlm.device
    }

@app.post("/analyze")
async def analyze(
    image: UploadFile = File(...),
    ml_kit_text: str = Form(...),
    ml_kit_blocks: str = Form(...)
):
    try:
        image_bytes = await image.read()
        image_pil = Image.open(io.BytesIO(image_bytes)).convert("RGB")
    except Exception as e:
        raise HTTPException(status_code=400, detail="Invalid image file.")

    try:
        # Aqui, poderíamos usar os blocos JSON para processamento extra,
        # mas por hora passamos o texto puro pro VLM
        result_dict = vlm.analyze(image_pil, ml_kit_text)
        return JSONResponse(content=result_dict)
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))

if __name__ == "__main__":
    import uvicorn
    uvicorn.run(app, host="0.0.0.0", port=8000)
