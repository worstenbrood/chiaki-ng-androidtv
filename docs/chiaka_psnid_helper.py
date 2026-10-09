import base64
import json
import urllib.parse
import urllib.request
import webbrowser
from http.server import BaseHTTPRequestHandler, HTTPServer

# Constants for official Sony PlayStation App OAuth configuration
CLIENT_ID = "09515159-7237-4370-b18d-7dbbc9e0354d"
REDIRECT_URI = "com.scee.psxwebservices.mainapp://redirect"
AUTH_URL = (
    f"https://sony.com"
    f"?access_type=offline&client_id={CLIENT_ID}"
    f"&redirect_uri={urllib.parse.quote(REDIRECT_URI)}"
    f"&response_type=code&scope=psn:client"
)


def get_account_id(auth_code):
    """Exchanges the authorization code for tokens and looks up the Account ID."""
    print("[*] Exchanging authorization code for an access token...")

    # Step 1: Exchange Auth Code for Access Token
    token_url = "https://sony.com"
    token_data = urllib.parse.urlencode(
        {
            "code": auth_code,
            "redirect_uri": REDIRECT_URI,
            "grant_type": "authorization_code",
            "token_format": "jwt",
        }
    ).encode("utf-8")

    token_headers = {
        "Authorization": "Basic MDk1MTUxNTktNzIzNy00MzcwLWIxOGQtN2RiYmM5ZTAzNTRkOnVjN2FkY2M4N2FjOWFjMmE=",
        "Content-Type": "application/x-www-form-urlencoded",
    }

    try:
        req = urllib.request.Request(
            token_url, data=token_data, headers=token_headers
        )
        with urllib.request.urlopen(req) as response:
            res_data = json.loads(response.read().decode())
            access_token = res_data.get("access_token")
    except Exception as e:
        print(f"[-] Token exchange failed: {e}")
        return

    # Step 2: Fetch the Profile Metadata using the Access Token
    print("[*] Querying PlayStation Network for your Account ID...")
    profile_url = (
        "https://playstation.com"
    )
    profile_headers = {"Authorization": f"Bearer {access_token}"}

    try:
        req = urllib.request.Request(profile_url, headers=profile_headers)
        with urllib.request.urlopen(req) as response:
            profile_data = json.loads(response.read().decode())
            account_id_str = profile_data.get("accountId")

            if account_id_str:
                # Convert the raw integer ID to the Base64 format Chiaki often expects
                account_id_int = int(account_id_str)
                bytes_id = account_id_int.to_bytes(8, byteorder="big")
                base64_id = (
                    base64.b64encode(bytes_id).decode("utf-8").strip("=")
                )

                print("\n" + "=" * 50)
                print(f"[+] SUCCESS! Your PSN Account ID: {account_id_str}")
                print(f"[+] Chiaki Registration Base64 ID: {base64_id}")
                print("=" * 50 + "\n")
            else:
                print("[-] Could not find accountId in the profile response.")
    except Exception as e:
        print(f"[-] Profile lookup failed: {e}")


class RedirectHandler(BaseHTTPRequestHandler):
    """Temporary local server to capture the browser redirect."""

    def log_message(self, format, *args):
        return  # Suppress standard logging to keep terminal output clean

    def do_GET(self):
        self.send_response(200)
        self.send_header("Content-type", "text/html")
        self.end_headers()

        # Send a success confirmation screen to the web browser
        html = """
        <html>
        <body style="font-family: sans-serif; text-align: center; padding-top: 50px; background-color: #f4f6f9;">
            <div style="display: inline-block; padding: 30px; background: white; border-radius: 8px; box-shadow: 0 4px 6px rgba(0,0,0,0.1);">
                <h2 style="color: #2e7d32;">Authorization Captured!</h2>
                <p>You may now close this tab and return to your terminal console.</p>
            </div>
        </body>
        </html>
        """
        self.wfile.write(html.encode("utf-8"))

        # Extract the auth code from the URL path string
        parsed_url = urllib.parse.urlparse(self.path)
        params = urllib.parse.parse_qs(parsed_url.query)

        if "code" in params:
            auth_code = params["code"][0]
            get_account_id(auth_code)
        else:
            print("[-] Authorization code parameter missing in redirect.")

        # Signal the server loop to shut down cleanly
        raise KeyboardInterrupt


def run():
    # Chiaki handles this locally by mapping the app url to localhost port 8080
    server_address = ("127.0.0.1", 8080)
    try:
        httpd = HTTPServer(server_address, RedirectHandler)
    except Exception as e:
        print(
            f"[-] Could not bind to port 8080: {e}. Check if another app is using it."
        )
        return

    print("[*] Opening your browser to the official Sony login portal...")
    webbrowser.open(AUTH_URL)
    print("[*] Awaiting your login completion... (Press Ctrl+C to stop)")

    try:
        httpd.handle_request()
    except KeyboardInterrupt:
        pass
    finally:
        print("[*] Helper script terminated.")


if __name__ == "__main__":
    run()
