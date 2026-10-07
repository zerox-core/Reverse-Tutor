"""Optional online enhancement APIs for the native local-first client."""

from fastapi import APIRouter

from .admin_routes import router as admin_router
from .auth_routes import router as auth_router
from .routes import router as online_router
from .stage_progress_routes import router as stage_progress_router

router = APIRouter()
router.include_router(auth_router)
router.include_router(online_router)
router.include_router(stage_progress_router)
router.include_router(admin_router)

__all__ = ["router"]
