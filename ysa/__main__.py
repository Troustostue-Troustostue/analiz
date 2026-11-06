"""Komut satırı arayüzü.

    python -m ysa calistir ornek_config.json
    python -m ysa tahmin  sonuc/en_iyi_model.json yeni_veri.xlsx [-o tahmin.xlsx]
    python -m ysa analiz  sonuc/en_iyi_model.json veri.xlsx          (Jacobian/Hessian raporu)
    python -m ysa bilgi
"""
import sys

from . import ysa_cli


def main(argv=None):
    return ysa_cli.main(argv)


if __name__ == "__main__":
    sys.exit(main())
