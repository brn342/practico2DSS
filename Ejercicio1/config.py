import os

class Config:
    DATABASE = os.path.join(os.path.dirname(os.path.abspath(__file__)), 'cine.db')
