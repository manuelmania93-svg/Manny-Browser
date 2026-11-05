from http.server import BaseHTTPRequestHandler, HTTPServer
class Handler(BaseHTTPRequestHandler):
    def do_GET(self):
        if self.path == '/download':
            payload=b'Manny Browser neutral download fixture\n'
            self.send_response(200)
            self.send_header('Content-Type','application/octet-stream')
            self.send_header('Content-Disposition','attachment; filename=manny-test.txt')
        else:
            second=self.path=='/second'
            payload=('''<!doctype html><html><head><meta name="viewport" content="width=device-width"><title>%s</title></head><body style="font:24px sans-serif;background:#fff;color:#111"><h1>%s</h1><p>Neutral browser fixture for navigation and find.</p><a href="/second">Second page</a><br><br><a href="/download">Download fixture</a></body></html>''' % ('Second fixture' if second else 'Manny fixture','Second fixture' if second else 'Manny fixture')).encode()
            self.send_response(200)
            self.send_header('Content-Type','text/html; charset=utf-8')
        self.send_header('Content-Length',str(len(payload)));self.end_headers();self.wfile.write(payload)
HTTPServer(('127.0.0.1',8765),Handler).serve_forever()
