from pydantic import BaseModel
from typing import List, Optional

class ConfidenceScores(BaseModel):
    platform_or_source: Optional[float] = None
    author_or_account: Optional[float] = None
    title: Optional[float] = None
    main_content: Optional[float] = None

class VLMResponse(BaseModel):
    content_type: str
    platform_or_source: Optional[str] = None
    author_or_account: Optional[str] = None
    published_at: Optional[str] = None
    title: Optional[str] = None
    subtitle: Optional[str] = None
    main_content: Optional[str] = None
    claims: List[str] = []
    discarded_text: List[str] = []
    confidence: Optional[ConfidenceScores] = None
