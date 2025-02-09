from locust import task, FastHttpUser


class HelloUser(FastHttpUser):
    @task
    def get_hello(self):
        response = self.client.get("/hello")
        if response.status_code != 200:
            print(f"Ошибка get_hello: {response.status_code} {response.text}")
