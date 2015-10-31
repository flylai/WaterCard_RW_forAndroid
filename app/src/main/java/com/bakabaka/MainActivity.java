package com.bakabaka;

import android.app.Activity;
import android.app.PendingIntent;
import android.app.DialogFragment;
import android.app.AlertDialog;
import android.app.Dialog;
import android.nfc.NfcAdapter;
import android.nfc.Tag;
import android.nfc.tech.MifareClassic;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.DialogInterface;
import android.os.Bundle;
import android.os.Build;
import android.widget.Toast;
import android.widget.TextView;
import android.widget.RadioButton;
import android.widget.EditText;
import android.widget.Button;
import android.view.View;
import android.provider.Settings;
import android.text.TextUtils;
import java.io.IOException;



public class MainActivity extends Activity 
{

	private static NfcAdapter mAdapter;

	byte[] keys = hexString2Byte("F3B1BF94E9D1");

	private String[][] mTechList;
	private IntentFilter[] mIntentFilters;
	private PendingIntent mPendingIntent;
	private Tag mTag;
	private String mData;
	private String valueStr;
	private int mSector = 14;
	private int mBlock1 = 1;
	private int mBlock2 = 2;

 	@Override
 	public void onCreate(Bundle savedInstanceState)
 	{
		super.onCreate(savedInstanceState);
		setContentView(R.layout.main);

		mAdapter = NfcAdapter.getDefaultAdapter(this);

		mTechList = new String[][] {new String[] { android.nfc.tech.MifareClassic.class.getName() }};
		mIntentFilters = new IntentFilter[] { new IntentFilter(NfcAdapter.ACTION_TECH_DISCOVERED), };
		mPendingIntent = PendingIntent.getActivity(this, 0, new Intent(this, getClass()).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP), 0);

		warningDialog warningDialog = new warningDialog();
		warningDialog.setCancelable(false);
		warningDialog.show(getFragmentManager(), "");

		getNfcStatus();
	}

	@Override
	protected void onNewIntent(Intent intent)
	{
		super.onNewIntent(intent);
		setIntent(intent);
		Button btn1 = (Button)findViewById(R.id.read_card);
		btn1.setClickable(true);
		Button btn2 = (Button)findViewById(R.id.write_card);
		btn2.setClickable(true);
		mTag = intent.getParcelableExtra(NfcAdapter.EXTRA_TAG);
		return;
	}

	public void getNfcStatus()
	{
		if (mAdapter == null || !mAdapter.isEnabled())
		{
			Button btn1 = (Button)findViewById(R.id.read_card);
			btn1.setClickable(false);
			Button btn2 = (Button)findViewById(R.id.write_card);
			btn2.setClickable(false);
			nfcStatusDialog nfcStatusDialog = new nfcStatusDialog();
			nfcStatusDialog.setCancelable(false);
			nfcStatusDialog.show(getFragmentManager(), "");
		}
	}

	public void onResume()
	{
		super.onResume();
		if (mAdapter != null && mAdapter.isEnabled())
		{
			mAdapter.enableForegroundDispatch(this, mPendingIntent, mIntentFilters, mTechList);
			if (NfcAdapter.ACTION_TECH_DISCOVERED.equals(getIntent().getAction()))
			{
				Toast.makeText(this, "发现新卡", Toast.LENGTH_SHORT).show();
			}
		}
 	}

	public static String byte2HexString(byte[] bytes)
	{
		String data = "";
		if (bytes != null)
		{
			for (Byte b : bytes)
			{
				data += String.format("%02X", b.intValue() & 0xFF);
			}
		}
		return data;
 	}


 	public static byte[] hexString2Byte(String str)
	{
		int len = str.length();
		byte[] data = new byte[len / 2];
		for (int i = 0; i < len; i += 2)
		{
			data[i / 2] = (byte) ((Character.digit(str.charAt(i), 16) << 4) + Character.digit(str.charAt(i + 1), 16));
		}

		return data;
 	}

	public void readTag(View view)
	{
		if (NfcAdapter.ACTION_TECH_DISCOVERED.equals(getIntent().getAction()))
		{
			Toast.makeText(this, "读卡中。", Toast.LENGTH_SHORT).show();
			processIntent(getIntent());
		}
		else
		{
			Toast.makeText(this, "你确定有卡么？反正读取失败了。", Toast.LENGTH_SHORT).show();
		}
	}


	public static String getTagId(Intent intent)
	{
		String id = null;
		if (NfcAdapter.ACTION_TECH_DISCOVERED.equals(intent.getAction()))
		{
			Tag tag = intent.getParcelableExtra(NfcAdapter.EXTRA_TAG);
			id = byte2HexString(tag.getId());
		}
		return id;
	}

	@Override
 	private void processIntent(Intent intent)
	{
		Tag tagFromIntent = intent.getParcelableExtra(NfcAdapter.EXTRA_TAG);
		boolean cardVerify = false;
		MifareClassic mfc = MifareClassic.get(tagFromIntent);
		try
		{
			String readLog = "";
			mfc.connect();
			int type = mfc.getType();
			if (type == MifareClassic.TYPE_CLASSIC)
			{
				readLog += "卡类型正确\n";
			}
			readLog += "卡号：" + getTagId(intent);
			cardVerify = mfc.authenticateSectorWithKeyB(mSector, keys);
			int bIndex;
			if (cardVerify)
			{
				bIndex = mfc.sectorToBlock(mSector) + mBlock2;
				byte[] data = mfc.readBlock(bIndex);
				mData = byte2HexString(data);
				//readLog += "Block " + bIndex + " : " + mData + "\n";
				double mon = getMoney(mData) / 100.00;
				readLog += "\n余额：" + mon;
			}
			else
			{
				readLog += "\n读取数据失败，请检查设备是否支持或卡拿对了没。";	
			}
			TextView promt=(TextView)findViewById(R.id.promt);
			promt.setText(readLog);
			mfc.close();
		}
		catch (Exception e)
		{
			Toast.makeText(this, "与卡的连接丢失，或设备不支持", Toast.LENGTH_SHORT).show();
		}
	}

	public void writeTag(String mData, int mMode, int intAddMoney)
	{
		MifareClassic mfc = MifareClassic.get(mTag);
		try
		{
			mfc.connect();
			boolean cardVerify = false;
			cardVerify = mfc.authenticateSectorWithKeyB(mSector, keys);
			if (cardVerify)
			{
				int block1 = mfc.sectorToBlock(mSector) + mBlock1;
				int block2 = mfc.sectorToBlock(mSector) + mBlock2;
				String dataTemp = willWriteMoney(mData, mMode, intAddMoney) + valueStr;

				mfc.writeBlock(block1, hexString2Byte(dataTemp));
				mfc.writeBlock(block2, hexString2Byte(dataTemp));

				Toast.makeText(this, "写入成功", Toast.LENGTH_SHORT).show();
				mfc.close();
			}
		}
		catch (IOException e)
		{
			Toast.makeText(this, "种种原因导致写入失败", Toast.LENGTH_SHORT).show();
		}
		processIntent(getIntent());
 	}

	public void writeMoney(View view)
	{

		if (NfcAdapter.ACTION_TECH_DISCOVERED.equals(getIntent().getAction()))
		processIntent(getIntent());

		RadioButton moneyClear = (RadioButton)findViewById(R.id.moneyClear);
		RadioButton moneyIncr = (RadioButton)findViewById(R.id.moneyIncr);
		RadioButton moneyDecr = (RadioButton)findViewById(R.id.moneyDecr);
		RadioButton moneyReplace = (RadioButton)findViewById(R.id.moneyReplace);

		int mMode = 0;

		if (moneyClear.isChecked())
		{
			mMode = 1;
		}
		else if (moneyIncr.isChecked())
		{
			mMode = 3;
		}
		else if (moneyDecr.isChecked())
		{
			mMode = 4;
		}
		else if (moneyReplace.isChecked())
		{
			mMode = 2;
		}

		EditText edit=(EditText)findViewById(R.id.num);
		String num = edit.getText().toString();
		if (!TextUtils.isEmpty(num) && !TextUtils.isEmpty(mData))
		{
			int i = Integer.parseInt(num);
			if (i > 3000)
			{
				Toast.makeText(this, "金额超出3000", Toast.LENGTH_SHORT).show();
			}
			else if (getMoney(mData) > 3000 && mMode == 3)
			{
				Toast.makeText(this, "卡余额尚充足", Toast.LENGTH_SHORT).show();
			}
			else
			{
				writeTag(mData, mMode, i);
			}
		}
		else
		{
			Toast.makeText(this, "数据错误", Toast.LENGTH_SHORT).show();
		}
	}

	public int getMoney(String mData)
	{

		String cardMoney = null;
		String str1 = mData.substring(0, 2);
		String str2 = mData.substring(2, 4);
		valueStr = mData.substring(4, 32);
		cardMoney = str2 + str1;
		Integer intCardMoney = Integer.parseInt(cardMoney, 16);
		return intCardMoney;
	}

	public String willWriteMoney(String mData, int mMode, int intAddMoney)
	{

		//1 清零
		//2 覆写
		//3 增值
		//4 减值

		int intWriteMoney;
		String cardMoney = null;
		int intCardMoney = getMoney(mData);

		if (mMode == 1)
		{
			cardMoney = "0000";
		}
		else if (mMode == 2)
		{
			cardMoney = Integer.toHexString(intAddMoney);
		}
		else if (mMode == 3)
		{
			intWriteMoney = intCardMoney + intAddMoney;
			if (intWriteMoney > 3000)
			{
				intWriteMoney = 3000;
			}
			cardMoney = Integer.toHexString(intWriteMoney);
		}
		else if (mMode == 4)
		{
			if (intCardMoney < intAddMoney)
			{
				intAddMoney = intCardMoney;
			}
			intWriteMoney = intCardMoney - intAddMoney;
			cardMoney = Integer.toHexString(intWriteMoney);
		}

		if (cardMoney.length() == 3)
		{
			cardMoney = "0" + cardMoney;
		}
		else if (cardMoney.length() == 2)
		{
			cardMoney = "00" + cardMoney;
		}
		else if (cardMoney.length() == 1)
		{
			cardMoney = "000" + cardMoney;
		}
		String str1 = cardMoney.substring(0, 2);
		String str2 = cardMoney.substring(2, 4);

		cardMoney = str2 + str1;

		return cardMoney;
	}
}

class warningDialog extends DialogFragment
{
	@Override
	public Dialog onCreateDialog(Bundle savedInstanceState)
	{
		AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
		builder.setTitle("重要警告");
		builder.setMessage("本软件仅供学习交流之用。\n将此软件用于不法行为者后果自负，与本人无关。");
		builder.setNegativeButton("什么玩意", new DialogInterface.OnClickListener() {

				@Override
				public void onClick(DialogInterface p1, int p2)
				{
					getActivity().finish();
				}
			});
		builder.setPositiveButton("我已明白", new DialogInterface.OnClickListener() {

				@Override
				public void onClick(DialogInterface p1, int p2)
				{

				}
			});
		return builder.create();
	}

}

class nfcStatusDialog extends DialogFragment
{
	@Override
	public Dialog onCreateDialog(Bundle savedInstanceState)
	{
		AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
		builder.setTitle("NFC错误");
		builder.setMessage("NFC未开启或者设备不支持NFC，或者手机NFC芯片不支持此卡。");
		builder.setPositiveButton("退出", new DialogInterface.OnClickListener() {

				@Override
				public void onClick(DialogInterface p1, int p2)
				{
					getActivity().finish();
				}
			});
		builder.setNegativeButton("NFC设置", new DialogInterface.OnClickListener() {

				@Override
				public void onClick(DialogInterface p1, int p2)
				{
					if (Build.VERSION.SDK_INT >= 16)
					{
						startActivity(new Intent(Settings.ACTION_NFC_SETTINGS));
					}
					else
					{
						startActivity(new Intent(Settings.ACTION_WIRELESS_SETTINGS));
					}
				}
			});
		return builder.create();
	}

}


