"""Optional online enhancement APIs for the native local-first client."""

from fastapi import APIRouter

from .auth_routes import router as auth_router
from .routes import router as online_router

router = APIRouter()
router.include_router(auth_router)
router.include_router(online_router)

__all__ = ["router"]
