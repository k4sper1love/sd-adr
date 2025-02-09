import itertools

from locust import task, FastHttpUser

client_id_generator = itertools.count(1)

class AuthUser(FastHttpUser):
    token = None

    def on_start(self):
        self.client_id = next(client_id_generator)

    @task
    def get_token_and_check(self):
        response = self.client.post("/token", json={"client_id": self.client_id})
        if response.status_code == 200:
            token = response.json().get("token")
            if token:
                headers = {"Authorization": token}
                self.client.get("/check", headers=headers)
        else:
            print(f"Ошибка получения токена: {response.text}")
