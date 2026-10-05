import torch
from transformers import Qwen2_5_VLForConditionalGeneration, AutoProcessor
from qwen_vl_utils import process_vision_info
import json
import re

class VLMService:
    def __init__(self):
        self.device = "mps" if torch.backends.mps.is_available() else "cpu"
        # Na inicialização, carrega o modelo
        print(f"Carregando Qwen2.5-VL-3B-Instruct no dispositivo {self.device}...")
        self.model = Qwen2_5_VLForConditionalGeneration.from_pretrained(
            "Qwen/Qwen2.5-VL-3B-Instruct",
            torch_dtype=torch.float16 if self.device == "mps" else torch.float32,
            device_map=self.device
        )
        self.processor = AutoProcessor.from_pretrained("Qwen/Qwen2.5-VL-3B-Instruct")
        self.loaded = True
        print("Modelo carregado com sucesso.")

    def analyze(self, image_pil, ml_kit_text: str):
        # Redimensionar para no máximo 1024x1024 preservando proporção
        image_pil.thumbnail((1024, 1024))

        prompt = f"""Analise a imagem deste print de notícia ou rede social e o texto extraído por OCR abaixo.

Texto do OCR:
{ml_kit_text}

Preencha as informações do print em formato JSON. Siga o esquema estritamente.
Instruções:
- content_type: "news_article", "social_post", "message", "advertisement", ou "unknown".
- Use a posição visual: diferencie o autor da postagem/notícia de pessoas mencionadas no texto.
- Diferencie nome de plataforma (ex: Instagram, Brasil 247) do nome do autor.
- Reconheça usuários e @handles.
- Separe publicação principal de comentários (comentários vão em discarded_text ou ignore).
- Ignore menus, botões, barras de status, anúncios e navegação, colocando-os em discarded_text.
- Não invente campos ausentes. Use null.
- Preserve o português original.
- NÃO avalie se a notícia é verdadeira ou falsa.
- 'claims' (afirmações verificáveis) devem ser completas e factuais.
- Responda APENAS com o JSON. Nenhuma palavra a mais.

Formato esperado:
{{
  "content_type": "string",
  "platform_or_source": "string|null",
  "author_or_account": "string|null",
  "published_at": "string|null",
  "title": "string|null",
  "subtitle": "string|null",
  "main_content": "string|null",
  "claims": ["string"],
  "discarded_text": ["string"],
  "confidence": {{
    "platform_or_source": float,
    "author_or_account": float,
    "title": float,
    "main_content": float
  }}
}}
"""

        messages = [
            {
                "role": "user",
                "content": [
                    {"type": "image", "image": image_pil},
                    {"type": "text", "text": prompt},
                ]
            }
        ]

        text = self.processor.apply_chat_template(messages, tokenize=False, add_generation_prompt=True)
        image_inputs, video_inputs = process_vision_info(messages)

        inputs = self.processor(
            text=[text],
            images=image_inputs,
            videos=video_inputs,
            padding=True,
            return_tensors="pt",
        ).to(self.device)

        with torch.no_grad():
            generated_ids = self.model.generate(**inputs, max_new_tokens=1024)

        generated_ids_trimmed = [
            out_ids[len(in_ids):] for in_ids, out_ids in zip(inputs.input_ids, generated_ids)
        ]

        output_text = self.processor.batch_decode(
            generated_ids_trimmed, skip_special_tokens=True, clean_up_tokenization_spaces=False
        )[0]

        return self.extract_json(output_text)

    def extract_json(self, text: str):
        # Encontra o primeiro { e o último }
        start = text.find("{")
        end = text.rfind("}")
        if start != -1 and end != -1:
            json_str = text[start:end+1]
            try:
                return json.loads(json_str)
            except:
                raise ValueError("Erro ao fazer parse do JSON retornado pelo modelo.")
        raise ValueError("JSON não encontrado na resposta.")
