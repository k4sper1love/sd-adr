package main

import (
	"errors"
	"github.com/dgrijalva/jwt-go"
	"github.com/gin-gonic/gin"
	"github.com/joho/godotenv"
	"gorm.io/gorm"
	"gorm.io/gorm/clause"
	"log"
	"net/http"
	"os"
	"sync"
	"time"
)

const tokenLifetime = 2 * time.Hour

type Claims struct {
	ClientId int
	jwt.StandardClaims
}

var (
	secretKey []byte
	cache     sync.Map
)

func main() {
	err := godotenv.Load()
	if err != nil {
		log.Println("not found .env file")
	}
	secretKey = []byte(os.Getenv("SECRET_KEY"))

	initDB()

	r := gin.Default()

	r.POST("/token", handleToken)

	r.GET("/check", handleCheck)

	r.Run(":8080")
}

func handleToken(c *gin.Context) {
	var client Client

	if err := c.ShouldBindJSON(&client); err != nil {
		c.JSON(http.StatusBadRequest, gin.H{"error": err.Error()})
		return
	}

	if value, ok := cache.Load(client.ClientID); ok {
		client := value.(Client)
		if time.Until(client.ExpiredAt) > 30*time.Minute {
			c.JSON(http.StatusOK, gin.H{"token": client.Token, "expired_at": client.ExpiredAt.Format(time.RFC3339)})
			return
		}
	}

	err := db.Where("client_id = ?", client.ClientID).First(&client).Error
	if err != nil && !errors.Is(err, gorm.ErrRecordNotFound) {
		c.JSON(http.StatusInternalServerError, gin.H{"error": "Database error"})
		return
	}

	if time.Until(client.ExpiredAt) > 30*time.Minute {
		cache.Store(client.ClientID, client)
		c.JSON(http.StatusOK, gin.H{"token": client.Token, "expired_at": client.ExpiredAt.Format(time.RFC3339)})
		return
	}

	tokenString, expTime, err := generateToken(client.ClientID)
	if err != nil {
		log.Println(err)
		c.JSON(http.StatusInternalServerError, gin.H{"error": "Token generation error"})
		return
	}

	client.Token = tokenString
	client.ExpiredAt = expTime

	err = db.Clauses(clause.OnConflict{
		Columns:   []clause.Column{{Name: "client_id"}},
		DoUpdates: clause.AssignmentColumns([]string{"token", "expired_at"}),
	}).Create(&client).Error

	if err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{"error": "Failed to save client"})
		return
	}

	cache.Store(client.ClientID, client)
	c.JSON(http.StatusOK, gin.H{"token": client.Token, "expired_at": client.ExpiredAt.Format(time.RFC3339)})
	return
}

func handleCheck(c *gin.Context) {
	tokenString := c.GetHeader("Authorization")
	if tokenString == "" {
		c.JSON(http.StatusUnauthorized, gin.H{"error": "Authorization header is empty"})
		return
	}

	token, err := jwt.ParseWithClaims(tokenString, &Claims{}, func(token *jwt.Token) (interface{}, error) {
		return secretKey, nil
	})
	if err != nil || !token.Valid {
		c.JSON(http.StatusUnauthorized, gin.H{"error": "Invalid token"})
		return
	}

	claims, ok := token.Claims.(*Claims)
	if !ok {
		c.JSON(http.StatusUnauthorized, gin.H{"error": "Invalid token claims"})
		return
	}

	c.JSON(http.StatusOK, gin.H{"message": "Successfully authorized", "client_id": claims.ClientId})
}

func generateToken(clientID int) (string, time.Time, error) {
	expTime := time.Now().Add(tokenLifetime)

	claims := &Claims{
		ClientId: clientID,
		StandardClaims: jwt.StandardClaims{
			ExpiresAt: expTime.Unix(),
		},
	}

	token := jwt.NewWithClaims(jwt.SigningMethodHS256, claims)
	tokenString, err := token.SignedString(secretKey)
	if err != nil {
		return "", time.Time{}, err
	}

	return tokenString, expTime, nil
}
