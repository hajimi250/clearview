"""启动入口：python run.py [--host 127.0.0.1] [--port 8000]"""
import argparse

import uvicorn

if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="澄影 ClearView 推理服务")
    parser.add_argument("--host", default="127.0.0.1")
    parser.add_argument("--port", type=int, default=8000)
    args = parser.parse_args()
    uvicorn.run("clearview_server.main:app", host=args.host, port=args.port)
