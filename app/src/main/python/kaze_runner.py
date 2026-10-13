import builtins
import ctypes
import importlib
import os
import runpy
import sys
import threading
import traceback

_tid = None


class _Out:
    def __init__(self, cb):
        self.cb = cb

    def write(self, s):
        if s:
            self.cb.invoke(str(s))
        return len(s) if s else 0

    def flush(self):
        pass

    def isatty(self):
        return False


def run(path, libdir, out, ask):
    """Run a Python file, streaming stdout/stderr to `out` and input() to `ask`."""
    global _tid
    _tid = threading.get_ident()
    saved = (sys.stdout, sys.stderr, sys.stdin, builtins.input, os.getcwd(), list(sys.path))
    sys.stdout = _Out(out)
    sys.stderr = _Out(out)

    def _input(prompt=""):
        print(prompt, end="")
        v = str(ask.invoke(str(prompt)))
        print(v)
        return v

    class _In:
        def readline(self):
            return _input() + "\n"

    sys.stdin = _In()
    builtins.input = _input
    folder = os.path.dirname(path)
    os.makedirs(libdir, exist_ok=True)
    for p in (libdir, folder):
        if p not in sys.path:
            sys.path.insert(0, p)
    importlib.invalidate_caches()
    try:
        os.chdir(folder)
        runpy.run_path(path, run_name="__main__")
    except SystemExit:
        pass
    except KeyboardInterrupt:
        print("\n[stopped]")
    except BaseException:
        etype, e, tb = sys.exc_info()
        tb = tb.tb_next.tb_next if tb and tb.tb_next and tb.tb_next.tb_next else tb  # hide runner frames
        print("".join(traceback.format_exception(etype, e, tb)))
    finally:
        sys.stdout, sys.stderr, sys.stdin, builtins.input, cwd, sp = saved
        os.chdir(cwd)
        sys.path[:] = sp


def stop():
    if _tid is not None:
        ctypes.pythonapi.PyThreadState_SetAsyncExc(ctypes.c_ulong(_tid), ctypes.py_object(KeyboardInterrupt))
