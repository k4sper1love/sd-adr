import os
from dotenv import load_dotenv
from sqlalchemy import create_engine, Integer, Column, String, DateTime
from sqlalchemy.ext.declarative import declarative_base
from sqlalchemy.orm import sessionmaker

load_dotenv()
engine = create_engine(os.getenv("DATABASE_URL"))
SessionLocal = sessionmaker(autocommit=False, autoflush=False, bind=engine)
Base = declarative_base()

class Client(Base):
    __tablename__ = 'clients'
    client_id = Column(Integer, primary_key=True)
    token = Column(String, nullable=False)
    expired_at = Column(DateTime, nullable=False)

def init_db():
    Base.metadata.create_all(bind=engine)