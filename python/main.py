import os
from datetime import timedelta, datetime, timezone
import jwt
from sqlalchemy.dialects.postgresql import insert
from aiocache import Cache
from fastapi import FastAPI, HTTPException, Depends, Header
from pydantic import BaseModel
from sqlalchemy.orm import Session
from database import Client, init_db, SessionLocal

SECRET_KEY = os.getenv('SECRET_KEY')
TOKEN_LIFETIME_HOURS = 2

app = FastAPI()
cache = Cache(Cache.MEMORY)
init_db()


class TokenRequest(BaseModel):
    client_id: int



def get_db():
    db = SessionLocal()
    try:
        yield db
    finally:
        db.close()


def generate_token(client_id: int):
    exp_time = datetime.now(timezone.utc) + timedelta(hours=TOKEN_LIFETIME_HOURS)
    exp_timestamp = exp_time.timestamp()
    data = {"client_id": client_id, "exp": exp_timestamp}
    return jwt.encode(data, SECRET_KEY, algorithm="HS256"), exp_time


@app.post("/token")
async def get_token(data: TokenRequest, db: Session = Depends(get_db)):
    if not data.client_id:
        raise HTTPException(status_code=400, detail="client_id is missing")

    cached_value = await cache.get(str(data.client_id))
    if cached_value:
        try:
            expired_at = datetime.fromisoformat(cached_value["expired_at"]).replace(
                tzinfo=timezone.utc)
            if expired_at > datetime.now(timezone.utc) + timedelta(minutes=30):
                return {
                    "token": cached_value["token"],
                    "expired_at": expired_at.isoformat(),
                }
        except ValueError:
            pass

    client = db.query(Client).filter(Client.client_id == data.client_id).first()
    if client:
        if client.expired_at.replace(tzinfo=timezone.utc) > datetime.now(timezone.utc) + timedelta(minutes=30):
            await cache.set(str(client.client_id), {
                "token": client.token,
                "expired_at": client.expired_at.isoformat(),
            })
            return {
                "token": client.token,
                "expired_at": client.expired_at.isoformat(),
            }

    token, exp_time = generate_token(data.client_id)

    query = insert(Client).values(
        client_id=data.client_id,
        token=token,
        expired_at=exp_time
    ).on_conflict_do_update(
        index_elements=["client_id"],
        set_={"token": token, "expired_at": exp_time}
    )
    db.execute(query)
    db.commit()

    await cache.set(str(data.client_id), {
        "token": token,
        "expired_at": exp_time.isoformat(),
    })

    return {
        "token": token,
        "expired_at": exp_time.isoformat(),
    }


@app.get("/check")
async def check_token(authorization: str = Header(None)):
    if not authorization:
        raise HTTPException(status_code=401, detail="Authorization token missing")

    try:
        payload = jwt.decode(authorization, SECRET_KEY, algorithms=["HS256"])
        exp_time = datetime.utcfromtimestamp(int(payload["exp"])).replace(tzinfo=timezone.utc)

        now_utc = datetime.now(timezone.utc)

        if now_utc > exp_time:
            print("token expired")
            raise HTTPException(status_code=401, detail="Token expired")

        return {
            "message": "Successfully authorized",
            "client_id": payload["client_id"],
        }
    except jwt.ExpiredSignatureError:
        raise HTTPException(status_code=401, detail="Token expired")
    except jwt.InvalidTokenError:
        raise HTTPException(status_code=401, detail="Invalid token")
