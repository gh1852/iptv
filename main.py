import requests
from bs4 import BeautifulSoup
import subprocess
import os

def get_stream_urls(channel):
    """
    Fetches the webpage and extracts stream URLs.
    """
    url = f"https://tonkiang.us/?iptv={channel}"
    urls = []
    headers = {
        'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36'
    }
    try:
        response = requests.get(url, headers=headers, timeout=10)
        response.raise_for_status()  # Raise an exception for bad status codes
        soup = BeautifulSoup(response.text, 'html.parser')
        # Find all tags with class 'uanga'
        for tag in soup.find_all(class_='uanga'):
            urls.append(tag.get_text().strip())
    except requests.exceptions.RequestException as e:
        print(f"Error fetching {url}: {e}")
    return urls

def analyze_stream(url):
    """
    Uses ffmpeg to analyze the stream metadata.
    """
    print(f"\n--- Analyzing: {url} ---")
    try:
        # Using ffprobe is often better for just getting info
        # but ffmpeg with -i will also work and show metadata.
        # We use -hide_banner to clean up the output a bit.
        command = ['ffmpeg', '-hide_banner', '-i', url]
        
        # We run the command and capture stderr because ffmpeg prints info there.
        # We set a timeout to avoid hanging on unresponsive streams.
        result = subprocess.run(
            command, 
            capture_output=True, 
            text=True, 
            timeout=30,
            # Prevent creation of a new window on Windows
            creationflags=subprocess.CREATE_NO_WINDOW if os.name == 'nt' else 0
        )
        
        # ffmpeg often exits with a non-zero code when it's just probing,
        # so we print stderr regardless of the return code.
        if result.stderr:
            print(result.stderr)
        else:
            print("No metadata found or stream is not responding.")

    except subprocess.TimeoutExpired:
        print("ffmpeg command timed out.")
    except FileNotFoundError:
        print("ffmpeg not found. Please ensure it is installed and in your system's PATH.")
    except Exception as e:
        print(f"An error occurred while analyzing stream: {e}")

def main():
    """
    Main function to run the script.
    """
    try:
        with open('list.txt', 'r', encoding='utf-8') as f:
            channels = [line.strip() for line in f if line.strip()]
        
        if not channels:
            print("list.txt is empty or not found.")
            return

        for channel in channels:
            print(f"--- Searching for channel: {channel} ---")
            stream_urls = get_stream_urls(channel)
            if stream_urls:
                for stream_url in stream_urls:
                    analyze_stream(stream_url)
            else:
                print(f"No stream URLs found for {channel}")

    except FileNotFoundError:
        print("Error: list.txt not found in the same directory as the script.")
    except Exception as e:
        print(f"An unexpected error occurred: {e}")

if __name__ == "__main__":
    main()