"""Deterministic exports. No Tally connection or tax filing is implied."""
from datetime import date
from decimal import Decimal
from xml.etree.ElementTree import Element, SubElement, tostring
from pydantic import BaseModel, Field, ConfigDict, field_validator

class SalesVoucher(BaseModel):
    model_config = ConfigDict(str_strip_whitespace=True)
    company: str = Field(min_length=1, max_length=120)
    customer_ledger: str = Field(min_length=1, max_length=120)
    sales_ledger: str = Field(min_length=1, max_length=120)
    voucher_number: str = Field(min_length=1, max_length=40)
    voucher_date: date
    amount: Decimal = Field(gt=0, le=Decimal('999999999.99'), max_digits=11, decimal_places=2)
    narration: str = Field(default='', max_length=1000)

    @field_validator('company', 'customer_ledger', 'sales_ledger', 'voucher_number', 'narration')
    @classmethod
    def valid_xml_text(cls, value):
        if any(ord(c) < 32 and c not in '\t\n\r' for c in value):
            raise ValueError('Control characters are not supported')
        return value


def sales_voucher_xml(req: SalesVoucher) -> str:
    root = Element('ENVELOPE')
    header = SubElement(root, 'HEADER')
    for tag, value in [('VERSION','1'), ('TALLYREQUEST','Import'), ('TYPE','Data'), ('ID','Vouchers')]:
        SubElement(header,tag).text=value
    body = SubElement(root, 'BODY')
    variables = SubElement(SubElement(body, 'DESC'), 'STATICVARIABLES')
    SubElement(variables, 'SVCURRENTCOMPANY').text = req.company
    voucher = SubElement(SubElement(SubElement(body, 'DATA'), 'TALLYMESSAGE'), 'VOUCHER', {'VCHTYPE':'Sales','ACTION':'Create'})
    for tag,value in [('DATE',req.voucher_date.strftime('%Y%m%d')),('VOUCHERTYPENAME','Sales'),
                      ('VOUCHERNUMBER',req.voucher_number),('PERSISTEDVIEW','Accounting Voucher View'),
                      ('ISINVOICE','No'),('PARTYLEDGERNAME',req.customer_ledger),('NARRATION',req.narration)]:
        SubElement(voucher, tag).text = value
    for name, amount, party in [(req.customer_ledger,-req.amount,True),(req.sales_ledger,req.amount,False)]:
        ledger=SubElement(voucher,'LEDGERENTRIES.LIST')
        for tag,value in [('LEDGERNAME',name),('ISDEEMEDPOSITIVE','Yes' if party else 'No'),
                          ('ISPARTYLEDGER','Yes' if party else 'No'),('AMOUNT',f'{amount:.2f}')]:
            SubElement(ledger,tag).text=value
    return tostring(root,encoding='unicode',xml_declaration=False)
