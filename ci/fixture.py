from http.server import BaseHTTPRequestHandler, HTTPServer
class Handler(BaseHTTPRequestHandler):
    def do_GET(self):
        if self.path == '/ads.js':
            # Fake ad script, requested via the emulator's host alias 10.0.2.2 so
            # the seeded host rule ||10.0.2.2^ can block it deterministically.
            payload=b"document.getElementById('marker').textContent='AD-LOADED';document.title='AD-LOADED';"
            self.send_response(200)
            self.send_header('Content-Type','application/javascript')
        elif self.path == '/download':
            payload=b'Manny Browser neutral download fixture\n'
            self.send_response(200)
            self.send_header('Content-Type','application/octet-stream')
            self.send_header('Content-Disposition','attachment; filename=manny-test.txt')
        elif self.path == '/adblock-test':
            payload=b'''<!doctype html><html><head><meta name="viewport" content="width=device-width"><title>Manny adblock fixture</title></head><body style="font:24px sans-serif;background:#fff;color:#111"><h1>Adblock fixture</h1><div id="marker">AD-PENDING</div><div class="ad-banner">SHOULD-BE-HIDDEN</div><script>function mark(t){document.getElementById('marker').textContent=t;document.title=t;}</script><script src="http://10.0.2.2:8765/ads.js" onerror="mark('AD-BLOCKED')"></script></body></html>'''
            self.send_response(200)
            self.send_header('Content-Type','text/html; charset=utf-8')
        elif self.path == '/popup-test':
            payload=b'''<!doctype html><html><head><meta name="viewport" content="width=device-width"><title>Manny popup fixture</title><script>window.onload=function(){window.open('http://127.0.0.1:8765/popup-land');};</script></head><body style="font:24px sans-serif;background:#fff;color:#111"><h1>Popup fixture</h1><a href="http://127.0.0.1:8765/popup-land" target="_blank">Open with gesture</a></body></html>'''
            self.send_response(200)
            self.send_header('Content-Type','text/html; charset=utf-8')
        elif self.path == '/popup-land':
            payload=b'''<!doctype html><html><head><meta name="viewport" content="width=device-width"><title>Popup landing</title></head><body style="font:24px sans-serif;background:#fff;color:#111"><h1>Popup landing</h1></body></html>'''
            self.send_response(200)
            self.send_header('Content-Type','text/html; charset=utf-8')
        else:
            second=self.path=='/second'
            payload=('''<!doctype html><html><head><meta name="viewport" content="width=device-width"><title>%s</title></head><body style="font:24px sans-serif;background:#fff;color:#111"><h1>%s</h1><p>Neutral browser fixture for navigation and find.</p><a href="/second">Second page</a><br><br><a href="/download">Download fixture</a></body></html>''' % ('Second fixture' if second else 'Manny fixture','Second fixture' if second else 'Manny fixture')).encode()
            self.send_response(200)
            self.send_header('Content-Type','text/html; charset=utf-8')
        self.send_header('Content-Length',str(len(payload)));self.end_headers();self.wfile.write(payload)
HTTPServer(('0.0.0.0',8765),Handler).serve_forever()
