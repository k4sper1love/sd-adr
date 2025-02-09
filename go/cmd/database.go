package main

import (
	"fmt"
	"gorm.io/driver/postgres"
	"gorm.io/gorm"
	"log"
	"os"
	"time"
)

type Client struct {
	ClientID  int       `json:"client_id" gorm:"primaryKey;uniqueIndex"`
	Token     string    `json:"token" gorm:"not null"`
	ExpiredAt time.Time `json:"expired_at" gorm:"not null"`
}

var db *gorm.DB

func initDB() {
	dsn := fmt.Sprintf("host=%s port=5432 user=%s password=%s dbname=%s sslmode=disable",
		os.Getenv("POSTGRES_HOST"),
		os.Getenv("POSTGRES_USER"),
		os.Getenv("POSTGRES_PASSWORD"),
		os.Getenv("POSTGRES_DB"),
	)

	var err error
	for {
		db, err = gorm.Open(postgres.Open(dsn), &gorm.Config{})
		if err != nil {
			log.Println("Ошибка подключения к бд: %v", err)
			continue
		}
		break
	}

	err = db.AutoMigrate(&Client{})
	if err != nil {
		log.Fatalf("Ошибка миграции бд: %v", err)
	}
}
